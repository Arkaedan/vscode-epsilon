package org.eclipse.epsilon.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
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
        languageServer.getWorkspaceFolders().addAll(event.getAdded());
        languageServer.getWorkspaceFolders().removeAll(event.getRemoved());
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        
    }
    
}
