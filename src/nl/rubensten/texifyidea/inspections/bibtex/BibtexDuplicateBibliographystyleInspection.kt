package nl.rubensten.texifyidea.inspections.bibtex

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import nl.rubensten.texifyidea.insight.InsightGroup
import nl.rubensten.texifyidea.inspections.TexifyInspectionBase
import nl.rubensten.texifyidea.psi.LatexCommands
import nl.rubensten.texifyidea.util.*

/**
 * @author Ruben Schellekens
 */
open class BibtexDuplicateBibliographystyleInspection : TexifyInspectionBase() {

    override fun getInspectionGroup() = InsightGroup.LATEX

    override fun getInspectionId() = "DuplicateBibliographystyle"

    // Manual override to match short name in plugin.xml
    override fun getShortName() = InsightGroup.BIBTEX.prefix + inspectionId

    override fun getDisplayName() = "Duplicate bibliography style commands"

    override fun inspectFile(file: PsiFile, manager: InspectionManager, isOntheFly: Boolean): MutableList<ProblemDescriptor> {
        val descriptors = descriptorList()

        // Check if a bibliography is present.
        val commands = file.commandsInFileSet()
        commands.find { it.name == "\\bibliography" } ?: return descriptors

        if (commands.findAtLeast(2) { it.name == "\\bibliographystyle" }) {
            file.commandsInFile()
                    .filter { it.name == "\\bibliographystyle" }
                    .forEach {
                        descriptors.add(manager.createProblemDescriptor(
                                it,
                                TextRange(0, it.commandToken.textLength),
                                "\\bibliographystyle is already used elsewhere",
                                ProblemHighlightType.GENERIC_ERROR,
                                isOntheFly,
                                RemoveOtherCommandsFix
                        ))
                    }
        }

        return descriptors
    }

    /**
     * @author Ruben Schellekens
     */
    object RemoveOtherCommandsFix : LocalQuickFix {

        override fun getFamilyName(): String {
            return "Remove other \\bibliographystyle commands"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val command = descriptor.psiElement as LatexCommands
            val file = command.containingFile
            val document = file.document() ?: return
            val editor = file.openedEditor() ?: return

            file.commandsInFileSet()
                    .filter { it.name == "\\bibliographystyle" && it != command }
                    .forEach {
                        it.delete()
                    }
        }
    }
}