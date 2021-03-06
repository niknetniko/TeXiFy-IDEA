package nl.hannahsten.texifyidea.inspections.latex

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import nl.hannahsten.texifyidea.insight.InsightGroup
import nl.hannahsten.texifyidea.inspections.TexifyInspectionBase
import nl.hannahsten.texifyidea.lang.magic.MagicCommentScope
import nl.hannahsten.texifyidea.psi.*
import nl.hannahsten.texifyidea.settings.TexifySettings
import nl.hannahsten.texifyidea.util.*
import nl.hannahsten.texifyidea.util.files.commandsAndFilesInFileSet
import java.util.*
import kotlin.reflect.jvm.internal.impl.utils.SmartList

/**
 * Currently only works for Chapters, Sections and Subsections.
 *
 * Planned is to also implement this for other environments.
 *
 * @author Hannah Schellekens
 */
open class LatexLabelConventionInspection : TexifyInspectionBase() {

    companion object {

        private fun getLabeledCommand(label: PsiElement): PsiElement? {
            return when (label) {
                is LatexCommands -> {
                    if (label.inDirectEnvironmentMatching {
                                Magic.Environment.labeled.containsKey(it.environmentName) &&
                                        !Magic.Environment.labelAsParameter.contains(it.environmentName)
                            }) {
                        label.parentOfType(LatexEnvironment::class)
                    }
                    else {
                        val grandparent = label.parent.parent
                        val sibling = LatexPsiUtil.getPreviousSiblingIgnoreWhitespace(grandparent) ?: return null
                        sibling.firstChildOfType(LatexCommands::class)
                    }
                }
                is LatexEnvironment -> {
                    label
                }
                else -> null
            }
        }

        /**
         * Finds the expected prefix for the supplied label
         */
        private fun getLabelPrefix(labeledCommand: PsiElement): String? {
            return when (labeledCommand) {
                is LatexCommands -> {
                    Magic.Command.labeled.getOrDefault(labeledCommand.name, null)
                }
                is LatexEnvironment -> {
                    Magic.Environment.labeled.getOrDefault(labeledCommand.environmentName, null)
                }
                else -> null
            }
        }
    }

    override val inspectionGroup = InsightGroup.LATEX

    override val inspectionId = "LabelConvention"

    override val ignoredSuppressionScopes = EnumSet.of(MagicCommentScope.COMMAND, MagicCommentScope.GROUP)!!

    override fun getDisplayName() = "Label conventions"

    override fun inspectFile(file: PsiFile, manager: InspectionManager, isOntheFly: Boolean): List<ProblemDescriptor> {
        val descriptors = SmartList<ProblemDescriptor>()
        checkLabels(file, manager, isOntheFly, descriptors)
        return descriptors
    }

    private fun checkLabels(file: PsiFile, manager: InspectionManager, isOntheFly: Boolean,
                            descriptors: MutableList<ProblemDescriptor>) {
        file.findLatexLabelPsiElementsInFileAsSequence().forEach{ label ->
            val labeledCommand = getLabeledCommand(label) ?: return@forEach
            val expectedPrefix = getLabelPrefix(labeledCommand)
            val labelName = label.extractLabelName()
            if (!expectedPrefix.isNullOrBlank() && !labelName.startsWith("$expectedPrefix:")) {
                descriptors.add(manager.createProblemDescriptor(
                        label,
                        "Unconventional label prefix",
                        LabelPreFix(),
                        ProblemHighlightType.WEAK_WARNING,
                        isOntheFly
                ))
            }
        }
    }

    /**
     * @author Hannah Schellekens
     */
    private class LabelPreFix : LocalQuickFix {

        override fun getFamilyName() = "Fix label name"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val command = descriptor.psiElement
            val baseFile = command.containingFile
            val oldLabel = command.extractLabelName()
            val latexPsiHelper = LatexPsiHelper(project)
            val labeledCommand = getLabeledCommand(command) ?: return

            // Determine label name.
            val prefix: String = getLabelPrefix(labeledCommand) ?: return
            val labelName = oldLabel.formatAsLabel()
            val createdLabelBase = if (labelName.contains(":")) {
                Magic.Pattern.labelPrefix.matcher(labelName).replaceAll("$prefix:")
            }
            else {
                "$prefix:$labelName"
            }

            val createdLabel = appendCounter(createdLabelBase, baseFile.findLatexAndBibtexLabelsInFileSet())

            // Replace in command label definition
            if (command is LatexCommands) {
                val position =
                        TexifySettings
                                .getInstance()
                                .labelPreviousCommands
                                .getOrDefault(command.name, null)
                                ?.position ?: return

                val labelParameter = command.requiredParameters().getOrNull(position - 1) ?: return
                labelParameter.replace(latexPsiHelper.createRequiredParameter(createdLabel))
            }

            // Replace in environment
            if (command is LatexEnvironment) {
                latexPsiHelper.replaceOptionalParameter(command.beginCommand.parameterList, "label", createdLabel)
            }

            // Replace in document.
            val filesAndReferences = findReferences(baseFile, oldLabel)
            for (pair in filesAndReferences) {
                val references = pair.second
                for (reference in references) {
                    reference.replace(latexPsiHelper.createRequiredParameter(createdLabel))
                }
            }

        }

        /**
         * Find all references to label `labelName`.
         */
        private fun findReferences(file: PsiFile, labelName: String): MutableSet<Pair<PsiFile, MutableList<LatexRequiredParam>>> {
            val result = mutableSetOf<Pair<PsiFile, MutableList<LatexRequiredParam>>>()

            val commandsAndFiles = file.commandsAndFilesInFileSet()

            // Loop over every file
            for (pair in commandsAndFiles) {
                // Only look at commands which refer to something
                val commands = pair.second.filter { Magic.Command.labelReference.contains(it.name) }.reversed()
                val requiredParams = mutableListOf<LatexRequiredParam>()

                // Find all the parameters with the given labelName
                for (ref in commands) {
                    val parameters = ref.requiredParameters().filter { p -> p.firstChildOfType(LatexParameterText::class)?.text == labelName }
                    requiredParams.addAll(parameters)
                }

                result.add(Pair(pair.first, requiredParams))
            }

            return result
        }

        /**
         * Keeps adding a counter behind the label until there is no other label with that name.
         */
        private fun appendCounter(label: String, allLabels: Set<String>): String {
            var counter = 2
            var candidate = label

            while (allLabels.contains(candidate)) {
                candidate = label + (counter++)
            }

            return candidate
        }
    }
}