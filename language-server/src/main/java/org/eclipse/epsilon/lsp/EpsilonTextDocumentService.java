package org.eclipse.epsilon.lsp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.emfatic.core.EmfaticResource;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.common.parse.Region;
import org.eclipse.epsilon.ecl.EclModule;
import org.eclipse.epsilon.egl.EglModule;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.eml.EmlModule;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.IEolModule;
import org.eclipse.epsilon.eol.dom.*;
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
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class EpsilonTextDocumentService implements TextDocumentService {

    protected EpsilonLanguageServer languageServer;
    protected Map<String, String> uriLanguageMap = new HashMap<>();
    protected Map<String, IEolModule> uriModuleMap = new HashMap<>();
    
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
        List<Diagnostic> diagnostics = Collections.emptyList();

        if (module != null) {
            try {
                module.parse(code, new File(new URI(uri)));
                diagnostics = getDiagnostics(module);
                // Cache the module for later use
                uriModuleMap.put(uri, module);
            }
            catch (Exception ex) {
                log(ex);
            }
        }
        else if (language.equals("emfatic")) {
            try {
                ResourceSet resourceSet = new ResourceSetImpl();
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("emf", new EmfaticResourceFactory());
                EmfaticResource resource = (EmfaticResource) resourceSet.createResource(org.eclipse.emf.common.util.URI.createURI(uri));
                resource.load(new ByteArrayInputStream(code.getBytes()), null);
                diagnostics = getDiagnostics(resource, code);
            }
            catch (Exception ex) {
                log(ex);
            }
        }
        else if (language.startsWith("flexmi-")) {
            try {
                ResourceSet resourceSet = new ResourceSetImpl();
                resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("flexmi", new FlexmiResourceFactory());
                FlexmiResource resource = (FlexmiResource) resourceSet.createResource(org.eclipse.emf.common.util.URI.createURI(uri));
                resource.load(new ByteArrayInputStream(code.getBytes()), null);
                diagnostics = getDiagnostics(resource, code);
            }
            catch (FlexmiParseException fex) {
                Position position = new Position(fex.getLineNumber(), 1);
                Diagnostic diagnostic = new Diagnostic();
                diagnostic.setMessage(fex.getMessage());
                diagnostic.setRange(new Range(position, position));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostics = Arrays.asList(diagnostic);
            }
            catch (Exception ex) {
                log(ex);
            }
        }

        final List<Diagnostic> theDiagnostics = diagnostics;
        CompletableFuture.runAsync(() -> {
            languageServer.getClient().publishDiagnostics(new PublishDiagnosticsParams(uri, theDiagnostics));
        });
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
            case "egl": return new EglModule();
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

    protected void log(Exception ex) {
        languageServer.getClient().logMessage(new MessageParams(MessageType.Error, ex.getMessage()));
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
		var position = params.getPosition();
        var moduleUri = params.getTextDocument().getUri();
        var module = uriModuleMap.get(moduleUri);
        if (module == null) {
            // Module not found, return empty list
            return CompletableFuture.completedFuture(Either.forLeft(Collections.emptyList()));
        }
        boolean isEGL = module instanceof EglModule;
        List<ModuleElement> elements;
        if (isEGL) {
            elements = getEglElementAtPosition(module, position);
        } else {
            elements = List.of(getElementAtPosition(module, position));
        }
        var locations = new ArrayList<Location>();
        for (ModuleElement element : elements) {
            if (element instanceof NameExpression) {
                element = element.getParent();
            }
            if (element instanceof OperationCallExpression) {
                var operationCall = (OperationCallExpression) element;
                var operationName = operationCall.getName();
                var availableOperations = module.getOperations();
                var foundOperations = new ArrayList<Operation>();
                for (Operation operation : availableOperations) {
                    if (operation.getName().equals(operationName)) {
                        foundOperations.add(operation);
                    }
                }
                for (Operation operation : foundOperations) {
                    locations.add(getLocation(operation));
                }
            }
            if (element instanceof StringLiteral) {
                element = element.getParent();
            }
            if (element instanceof Import) {
                var importElement = (Import) element;
                var uri = importElement.getModule().getSourceUri().toString();
                var location = new Location();
                location.setUri(uri);
                location.setRange(new Range(new Position(0, 0), new Position(0, 0)));
                locations.add(location);
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(locations));
	}

    protected Location getLocation(Operation operation) {
        var uri = operation.getModule().getUri().toString();
        var region = operation.getRegion();
        var start = region.getStart();
        var end = region.getEnd();
        var location = new Location();
        location.setUri(uri);
        location.setRange(new Range(new Position(start.getLine() - 1, start.getColumn()), new Position(end.getLine() - 1, end.getColumn())));
        return location;
    }

    protected boolean regionContainsPosition(Region region, Position position) {
        var start = region.getStart();
        var end = region.getEnd();
        // Position uses zero-based indexing, but Regions use one-based indexing
        var positionLine = position.getLine() + 1;
        var positionChar = position.getCharacter() + 1;
        if (positionLine < start.getLine() || positionLine > end.getLine()) {
            // position is before or after the region
            return false;
        }
        if (start.getLine() == end.getLine()) {
            // region exists as a section of a single line,
            // and position must also be on this line due to failing previous if
            return positionChar >= start.getColumn() && positionChar <= end.getColumn();
        }
        if (positionLine == start.getLine()) {
            return positionChar >= start.getColumn();
        }
        if (positionLine == end.getLine()) {
            return positionChar <= end.getColumn();
        }
        return true;
    }

    protected ModuleElement getElementAtPosition(ModuleElement parent, Position position) {
        for (ModuleElement child : parent.getChildren()) {
            if (regionContainsPosition(child.getRegion(), position)) {
                return getElementAtPosition(child, position);
            }
        }
        return parent;
    }

    // EGL has overlapping elements so we need to get all possible elements at the position
    protected List<ModuleElement> getElementsAtPosition(ModuleElement parent, Position position) {
        List<ModuleElement> elements = new ArrayList<>();

        for (ModuleElement child : parent.getChildren()) {
            if (regionContainsPosition(child.getRegion(), position)) {
                elements.addAll(getElementsAtPosition(child, position));
            }
        }

        if (elements.isEmpty()) {
            elements.add(parent);
        }

        return elements;
    }

    protected List<ModuleElement> getEglElementAtPosition(ModuleElement parent, Position position) {
        List<ModuleElement> elements = getElementsAtPosition(parent, position);
        List<ModuleElement> filteredElements = new ArrayList<>();
        for (ModuleElement element : elements) {
            if (!shouldIgnoreEglElement(element)) {
                filteredElements.add(element);
            }
        }

        return filteredElements;
    }

    protected boolean shouldIgnoreEglElement(ModuleElement element) {
        if (element instanceof NameExpression) {
            var nameExpression = (NameExpression) element;
            var name = nameExpression.getName();
            return name.equals("out") || name.equals("printdyn") || name.equals("_outdent");
        }

        if (element instanceof StringLiteral) {
            var parent = element.getParent();
            if (parent instanceof OperationCallExpression) {
                var operationCall = (OperationCallExpression) parent;
                var operationName = operationCall.getName();
                return operationName.equals("_outdent");
            }
        }

        return false;
    }

}
