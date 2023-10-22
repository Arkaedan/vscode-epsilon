package org.eclipse.epsilon.lsp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.emfatic.core.EmfaticResource;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.ecl.EclModule;
import org.eclipse.epsilon.egl.EglTemplateFactoryModuleAdapter;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.eml.EmlModule;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.IEolModule;
import org.eclipse.epsilon.epl.EplModule;
import org.eclipse.epsilon.etl.EtlModule;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.flexmi.FlexmiParseException;
import org.eclipse.epsilon.flexmi.FlexmiResource;
import org.eclipse.epsilon.flexmi.FlexmiResourceFactory;
import org.eclipse.epsilon.flock.FlockModule;
import org.eclipse.epsilon.pinset.PinsetModule;
import org.eclipse.gymnast.runtime.core.parser.ParseError;
import org.eclipse.gymnast.runtime.core.parser.ParseMessage;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.TextDocumentService;

public class EpsilonTextDocumentService implements TextDocumentService {

    protected EpsilonLanguageServer languageServer;
    protected Map<String, String> uriLanguageMap = new HashMap<>();
    
    public EpsilonTextDocumentService(EpsilonLanguageServer languageServer) {
        this.languageServer = languageServer;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {

        // Remember the ID of the language used to edit this file as the ID
        // is not provided again in didChange
        uriLanguageMap.put(params.getTextDocument().getUri(), params.getTextDocument().getLanguageId());

        publishDiagnostics(params.getTextDocument().getText(), 
            params.getTextDocument().getUri(), 
            params.getTextDocument().getLanguageId());
    }
    
    protected List<Diagnostic> getDiagnostics(EmfaticResource resource, String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ParseMessage parseMessage : resource.getParseContext().getMessages()) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setSeverity(parseMessage instanceof ParseError ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning);
            diagnostic.setMessage(parseMessage.getMessage());
            //TODO: Emfatic produces messages with "at line X, column Y" suffix, which are more accurate than the offset/length
            diagnostic.setRange(new Range(getPosition(text, parseMessage.getOffset()), getPosition(text, parseMessage.getOffset() + parseMessage.getLength())));
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    protected List<Diagnostic> getDiagnostics(FlexmiResource resource, String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(getDiagnostics(resource.getWarnings(), DiagnosticSeverity.Warning));
        diagnostics.addAll(getDiagnostics(resource.getErrors(), DiagnosticSeverity.Error));
        return diagnostics;
    }

    protected Collection<Diagnostic> getDiagnostics(List<org.eclipse.emf.ecore.resource.Resource.Diagnostic> emfDiagnostics, DiagnosticSeverity severity) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (org.eclipse.emf.ecore.resource.Resource.Diagnostic emfDiagnostic : emfDiagnostics) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setMessage(emfDiagnostic.getMessage());
            diagnostic.setSeverity(severity);
            Position position = new Position(emfDiagnostic.getLine() - 1, emfDiagnostic.getColumn());
            diagnostic.setRange(new Range(position, position));
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    protected List<Diagnostic> getDiagnostics(IEolModule module) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ParseProblem problem : module.getParseProblems()) {
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setSeverity(problem.getSeverity() == ParseProblem.ERROR ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning);
            diagnostic.setMessage(problem.getReason());
            diagnostic.setRange(new Range(
                    new Position(problem.getLine() - 1, Math.max(problem.getColumn(),0)),
                    new Position(problem.getLine() - 1, Math.max(problem.getColumn(),0))));
            diagnostics.add(diagnostic);
        }
        return diagnostics;
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        publishDiagnostics(params.getContentChanges().get(0).getText(), 
            params.getTextDocument().getUri(), 
            uriLanguageMap.get(params.getTextDocument().getUri()));
    }

    public void publishDiagnostics(String code, String uri, String language) {
        IEolModule module = createModule(language);

        if (module != null) {
            try {
                module.parse(code, new File(new URI(uri)));
                CompletableFuture.runAsync(() -> {
                    languageServer.getClient().publishDiagnostics(new PublishDiagnosticsParams(uri, getDiagnostics(module)));
                });
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (language.equals("emfatic")) {
            try {
                ResourceSet resourceSet = new ResourceSetImpl();
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());
                EmfaticResource resource = (EmfaticResource) resourceSet.createResource(org.eclipse.emf.common.util.URI.createURI(uri));
                resource.load(new ByteArrayInputStream(code.getBytes()), null);
                CompletableFuture.runAsync(() -> {
                    languageServer.getClient().publishDiagnostics(new PublishDiagnosticsParams(uri, getDiagnostics(resource, code)));
                });
                
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else if (language.startsWith("flexmi-")) {
            try {
                ResourceSet resourceSet = new ResourceSetImpl();
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("flexmi", new FlexmiResourceFactory());
                FlexmiResource resource = (FlexmiResource) resourceSet.createResource(org.eclipse.emf.common.util.URI.createURI(uri));

                // Since we have no way of registering Emfatic files in the LSP server (yet)
                // we need to search for all Emfatic files in the workspace and register them 
                // in the global EPackage registry before attempting to load the flexmi resource
                // TODO: We should really do this on startup (instead of every time we need to parse a Flexmi file) and then monitor the workspace for relevant (Emfatic) file changes/additions/removals via the EpsilonWorkspaceService to keep the global EPackage registry up to date
                List<File> emfaticFiles = new ArrayList<>();
                for (WorkspaceFolder workspaceFolder : languageServer.getWorkspaceFolders()) {
                    String[] extensions = new String[]{"emf"};
                    emfaticFiles.addAll(FileUtils.listFiles(new File(URI.create(workspaceFolder.getUri())), extensions, false));
                }

                for (File emfaticFile : emfaticFiles) {
                    ResourceSet emfaticResourceSet = new ResourceSetImpl();
                    emfaticResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());
                    EmfaticResource emfaticResource = (EmfaticResource) emfaticResourceSet.createResource(org.eclipse.emf.common.util.URI.createFileURI(emfaticFile.getAbsolutePath()));
                    emfaticResource.load(null);
                    EPackage ePackage = (EPackage) emfaticResource.getContents().get(0);
                    EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
                }

                // Once we have registered all Emfatic files in the workspace, we can parse the Flexmi model
                resource.load(new ByteArrayInputStream(code.getBytes()), null);
                CompletableFuture.runAsync(() -> {
                    languageServer.getClient().publishDiagnostics(new PublishDiagnosticsParams(uri, getDiagnostics(resource, code)));
                });
            }
            catch (FlexmiParseException fex) {
                
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else {
            System.out.println("Don't know what to do");
        }
    }

    protected Position getPosition(String code, int offset) {
        int line = 0;
        int column = 0;
        int consumed = 0;

        for (char ch : code.toCharArray()) {
            if (consumed == offset) break;
            consumed ++;
            if (System.lineSeparator().equals(ch + "")) {
                line++;
                column=0;
            }
            else {
                column++;
            }
        }
        return new Position(line, column);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        uriLanguageMap.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }
    
    protected IEolModule createModule(String languageId) {
        switch (languageId) {
            case "evl": return new EvlModule();
            case "etl": return new EtlModule();
            case "egl": return new EglTemplateFactoryModuleAdapter();
            case "egx": return new EgxModule();
            case "ecl": return new EclModule();
            case "eml": return new EmlModule();
            case "mig": return new FlockModule();
            case "pinset": return new PinsetModule();
            case "epl": return new EplModule();
            case "eol": return new EolModule();
            default: return null;
        }
    }

}
