package com.intellij.compiler.artifacts;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public abstract class ArtifactsTestCase extends IdeaTestCase {
  protected boolean mySetupModule;

  protected ArtifactManager getArtifactManager() {
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  protected void setUpModule() {
    if (mySetupModule) {
      super.setUpModule();
    }
  }

  protected void deleteArtifact(final Artifact artifact) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.removeArtifact(artifact);
    commitModel(model);
  }

  protected static void commitModel(final ModifiableArtifactModel model) {
    new WriteAction() {
      protected void run(final Result result) {
        model.commit();
      }
    }.execute();
  }

  protected Artifact rename(Artifact artifact, String newName) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(artifact).setName(newName);
    commitModel(model);
    return artifact;
  }

  protected Artifact addArtifact(String name) {
    return addArtifact(name, null);
  }

  protected Artifact addArtifact(String name, final CompositePackagingElement<?> root) {
    return addArtifact(name, PlainArtifactType.getInstance(), root);
  }

  protected Artifact addArtifact(final String name, final ArtifactType type, final CompositePackagingElement<?> root) {
    return getArtifactManager().addArtifact(name, type, root);
  }

  protected PackagingElementResolvingContext getContext() {
    return ArtifactManager.getInstance(myProject).getResolvingContext();
  }

  protected void renameFile(final VirtualFile file, final String newName) {
    new WriteAction() {
      protected void run(final Result result) {
        try {
          file.rename(this, newName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
  }

  protected Module addModule(final String moduleName, final @Nullable VirtualFile sourceRoot) {
    return new WriteAction<Module>() {
      protected void run(final Result<Module> result) {
        final Module module = createModule(moduleName);
        if (sourceRoot != null) {
          PsiTestUtil.addSourceContentToRoots(module, sourceRoot);
        }
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        model.setSdk(getTestProjectJdk());
        model.commit();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  protected class MockPackagingEditorContext implements ArtifactEditorContext {
    private ModifiableArtifactModel myModifiableModel;
    private Map<Module, ModifiableRootModel> myModifiableRootModels = new HashMap<Module, ModifiableRootModel>();
    private Map<CompositePackagingElement<?>, ManifestFileConfiguration> myManifestFiles = new HashMap<CompositePackagingElement<?>, ManifestFileConfiguration>();

    @NotNull
    public ModifiableArtifactModel getOrCreateModifiableArtifactModel() {
      if (myModifiableModel == null) {
        myModifiableModel = ArtifactManager.getInstance(myProject).createModifiableModel();
      }
      return myModifiableModel;
    }

    public ModifiableModuleModel getModifiableModuleModel() {
      return null;
    }

    @NotNull
    public ModifiableRootModel getOrCreateModifiableRootModel(@NotNull Module module) {
      ModifiableRootModel model = myModifiableRootModels.get(module);
      if (model == null) {
        model = ModuleRootManager.getInstance(module).getModifiableModel();
        myModifiableRootModels.put(module, model);
      }
      return model;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public ArtifactModel getArtifactModel() {
      if (myModifiableModel != null) {
        return myModifiableModel;
      }
      return ArtifactManager.getInstance(myProject);
    }

    @NotNull
    public ModulesProvider getModulesProvider() {
      return new DefaultModulesProvider(myProject);
    }

    @NotNull
    public FacetsProvider getFacetsProvider() {
      return DefaultFacetsProvider.INSTANCE;
    }

    public void queueValidation() {
    }

    public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
      final VirtualFile manifestFile = ManifestFileUtil.findManifestFile(element, this, PlainArtifactType.getInstance());
      if (manifestFile == null) {
        return null;
      }

      ManifestFileConfiguration configuration = myManifestFiles.get(element);
      if (configuration == null) {
        configuration = ManifestFileUtil.createManifestFileConfiguration(manifestFile);
        myManifestFiles.put(element, configuration);
      }
      return configuration;
    }

    public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException("'getRootElement' not implemented in " + getClass().getName());
    }

    public ArtifactEditor getOrCreateEditor(Artifact artifact) {
      throw new UnsupportedOperationException("'getOrCreateEditor' not implemented in " + getClass().getName());
    }

    public ArtifactEditor getThisArtifactEditor() {
      throw new UnsupportedOperationException("'getThisArtifactEditor' not implemented in " + getClass().getName());
    }

    public void selectArtifact(@NotNull Artifact artifact) {
    }

    public void selectFacet(@NotNull Facet<?> facet) {
    }

    public void selectModule(@NotNull Module module) {
    }

    public void selectLibrary(@NotNull Library library) {
    }

    public void editLayout(@NotNull Artifact artifact, Runnable runnable) {
    }

    @NotNull
    public ArtifactType getArtifactType() {
      throw new UnsupportedOperationException("'getArtifactType' not implemented in " + getClass().getName());
    }

    public List<Artifact> chooseArtifacts(List<? extends Artifact> artifacts, String title) {
      throw new UnsupportedOperationException("'chooseArtifacts' not implemented in " + getClass().getName());
    }

    public List<Module> chooseModules(List<Module> modules, String title) {
      throw new UnsupportedOperationException("'chooseModules' not implemented in " + getClass().getName());
    }

    public List<Library> chooseLibraries(List<Library> libraries, String title) {
      throw new UnsupportedOperationException("'chooseLibraries' not implemented in " + getClass().getName());
    }

    public Artifact getArtifact() {
      throw new UnsupportedOperationException("'getArtifact' not implemented in " + getClass().getName());
    }
  }
}
