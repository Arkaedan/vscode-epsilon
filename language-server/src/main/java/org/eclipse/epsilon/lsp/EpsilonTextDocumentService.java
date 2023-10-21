package org.eclipse.epsilon.lsp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.lsp4j.services.TextDocumentService;

public class EpsilonTextDocumentService implements TextDocumentService {

    protected EpsilonLanguageServer languageServer;
    protected Map<String, String> uriLanguageMap = new HashMap<>();
    
    public EpsilonTextDocumentService(EpsilonLanguageServer languageServer) {
        this.languageServer = languageServer;
    }

    /*
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        List<CompletionItem> completionItems = new ArrayList<>();

        completionItems.add(new CompletionItem("First Suggestion"));
        completionItems.add(new CompletionItem("Second Suggestion"));
        completionItems.add(new CompletionItem("Third Suggestion"));

        return CompletableFuture.completedFuture(Either.forLeft(completionItems));
    }*/

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
