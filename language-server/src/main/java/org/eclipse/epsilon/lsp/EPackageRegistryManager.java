package org.eclipse.epsilon.lsp;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.lsp4j.WorkspaceFolder;

/**
 This class monitors the workspace through <code>EpsilonWorkspaceService</code> and keeps EPackage.Registry.Instance up to date with all the Emfatic files in the workspace. It auto-registers all EPackages in Emfatic files that parse correctly and removes EPackages from Emfatic files which do not parse correctly or have been removed from the workspace. 
 */
public class EPackageRegistryManager {

    // A map linking the Emfatic files to the EPackages that have been parsed out of them
    protected Map<String, List<EPackage>> fileEPackages = new LinkedHashMap<>();

    public void initialize(List<WorkspaceFolder> workspaceFolders) {
        workspaceFolders.forEach(workspaceFolder -> addWorkspaceFolder(workspaceFolder));
    }
    
    protected List<EPackage> getEPackages(File file) {
        try {
            ResourceSet resourceSet = new ResourceSetImpl();
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());
            Resource resource = resourceSet.createResource(org.eclipse.emf.common.util.URI.createFileURI(file.getAbsolutePath()));
            resource.load(null);

            // Extract the top-level EPackage and any nested EPackages
            List<EPackage> ePackages = new ArrayList<>();
            resource.getAllContents().forEachRemaining(element -> {
                if (element instanceof EPackage) ePackages.add((EPackage) element);
            });
            return ePackages;
        }
        catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    protected void removeWorkspaceFolder(WorkspaceFolder workspaceFolder) {
        getEmfaticFiles(workspaceFolder).forEach(file -> removeFile(file));
    }

    protected void addWorkspaceFolder(WorkspaceFolder workspaceFolder) {
        getEmfaticFiles(workspaceFolder).forEach(file -> addFile(file));
    }

    protected void addFile(File file) {
        // When an Emfatic file is added, extract all EPackages from it
        // and register them in the global EPackage registry
        List<EPackage> ePackages = getEPackages(file);
        fileEPackages.put(file.getAbsolutePath(), ePackages);
        for (EPackage ePackage : ePackages) {
            EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
        }
    }

    protected void removeFile(File file) {
        // When an Emfatic file is removed from the workspace, remove any EPackages
        // extracted from it from the global EPackage registry
        if (fileEPackages.containsKey(file.getAbsolutePath())) {
            fileEPackages.remove(file.getAbsolutePath()).forEach(
                ePackage -> EPackage.Registry.INSTANCE.remove(ePackage.getNsURI()));
        }
    }

    protected Collection<File> getEmfaticFiles(WorkspaceFolder workspaceFolder) {
        // Return all files with a .emf extension anywhere under the workspace folder
        return FileUtils.listFiles(new File(URI.create(workspaceFolder.getUri())), new String[]{"emf"}, true);
    }
    
}
