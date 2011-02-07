/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPsiElementPointerImpl");

  private Reference<E> myElement;
  private final SmartPointerElementInfo myElementInfo;
  private final Project myProject;
  private final Class<? extends PsiElement> myElementClass;

  public SmartPsiElementPointerImpl(@NotNull Project project, @NotNull E element, PsiFile containingFile) {
    myProject = project;
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myElement = new WeakReference<E>(element);
    myElementInfo = createElementInfo(element, containingFile);
    myElementClass = element.getClass();

    // Assert document committed.
    //todo
    //if (containingFile != null) {
    //  final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    //  if (psiDocumentManager instanceof PsiDocumentManagerImpl) {
    //    Document doc = psiDocumentManager.getCachedDocument(containingFile);
    //    if (doc != null) {
    //      //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
    //      if (!(element instanceof PsiFile)) {
    //        LOG.assertTrue(!psiDocumentManager.isUncommited(doc) || ((PsiDocumentManagerImpl)psiDocumentManager).isCommittingDocument(doc));
    //      }
    //    }
    //  }
    //}
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof SmartPsiElementPointer)) return false;
    SmartPsiElementPointer pointer = (SmartPsiElementPointer)obj;
    return SmartPointerManager.getInstance(myProject).pointToTheSameElement(this, pointer);
  }

  public int hashCode() {
    return myElementInfo.elementHashCode();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  public E getElement() {
    PsiElement element = getCachedElement();
    if (element != null && !element.isValid()) {
      element = null;
    }
    if (element == null && myElementInfo != null) {
      element = myElementInfo.restoreElement();
      if (element != null && (!element.getClass().equals(myElementClass) || !element.isValid())) {
        element = null;
      }

      myElement = element == null ? null : new WeakReference(element);
    }

    return (E)element;
  }

  private E getCachedElement() {
    return myElement == null ? null : myElement.get();
  }

  public PsiFile getContainingFile() {
    E element = getCachedElement();
    if (element != null) {
      return element.getContainingFile();
    }
    VirtualFile virtualFile = myElementInfo.getVirtualFile();
    if (virtualFile != null && virtualFile.isValid()) {
      PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
      if (psiFile != null) return psiFile;
    }

    final Document doc = myElementInfo.getDocumentToSynchronize();
    if (doc == null) {
      final E resolved = getElement();
      return resolved != null ? resolved.getContainingFile() : null;
    }
    return PsiDocumentManager.getInstance(myProject).getPsiFile(doc);
  }

  public VirtualFile getVirtualFile() {
    return myElementInfo.getVirtualFile();
  }

  @Override
  public Segment getSegment() {
    return myElementInfo.getSegment();
  }

  @NotNull
  private SmartPointerElementInfo createElementInfo(@NotNull E element, PsiFile containingFile) {
    if (element instanceof PsiCompiledElement || !element.isPhysical() || containingFile == null || element.getTextRange() == null) {
      return new HardElementInfo(element);
    }

    for(SmartPointerElementInfoFactory factory: Extensions.getExtensions(SmartPointerElementInfoFactory.EP_NAME)) {
      final SmartPointerElementInfo result = factory.createElementInfo(element);
      if (result != null) {
        return result;
      }
    }

    if (element instanceof PsiFile) {
      return new FileElementInfo((PsiFile)element);
    }

    if (containingFile.getContext() != null) {
      return new InjectedSelfElementInfo(element, containingFile);
    }

    return new SelfElementInfo(element, containingFile);
  }

  public void documentAndPsiInSync() {
    if (myElementInfo != null) {
      myElementInfo.documentAndPsiInSync();
    }
  }

  @Override
  public void dispose() {
    if (myElementInfo != null) {
      myElementInfo.dispose();
      myElement = null;
    }
  }

  @Override
  public void unfastenBelt(int offset) {
    myElementInfo.unfastenBelt(offset);
  }

  public void fastenBelt(int offset) {
    myElementInfo.fastenBelt(offset);
  }

  SmartPointerElementInfo getElementInfo() {
    return myElementInfo;
  }
}
