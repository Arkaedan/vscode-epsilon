package org.eclipse.epsilon.lsp;

import java.io.File;
import java.net.URI;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.services.WorkspaceService;

public class EpsilonWorkspaceService implements WorkspaceService {
    
    protected EpsilonLanguageServer languageServer;

    public EpsilonWorkspaceService(EpsilonLanguageServer languageServer) {
        this.languageServer = languageServer;
    }
    
    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        WorkspaceFoldersChangeEvent event = params.getEvent();
        event.getAdded().forEach(workspaceFolder -> languageServer.getEPackageRegistryManager().addWorkspaceFolder(workspaceFolder));
        event.getRemoved().forEach(workspaceFolder -> languageServer.getEPackageRegistryManager().removeWorkspaceFolder(workspaceFolder));        
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        for (FileEvent event : params.getChanges()) {
            if (!event.getUri().endsWith(".emf")) continue;
            System.out.println(event);
            if (event.getType() == FileChangeType.Changed) {
                languageServer.getEPackageRegistryManager().removeFile(new File(URI.create(event.getUri())));
                languageServer.getEPackageRegistryManager().addFile(new File(URI.create(event.getUri())));
            }
            else if (event.getType() == FileChangeType.Created) {
                languageServer.getEPackageRegistryManager().addFile(new File(URI.create(event.getUri())));
            }
            else if (event.getType() == FileChangeType.Deleted) {
                languageServer.getEPackageRegistryManager().removeFile(new File(URI.create(event.getUri())));
            }
        }
    }
    
}
