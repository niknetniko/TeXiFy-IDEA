// This is a generated file. Not intended for manual editing.
package nl.rubensten.texifyidea.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface BibtexEntry extends PsiElement {

  @NotNull
  BibtexEndtry getEndtry();

  @Nullable
  BibtexEntryContent getEntryContent();

  @Nullable
  BibtexPreamble getPreamble();

  @NotNull
  BibtexType getType();

}
