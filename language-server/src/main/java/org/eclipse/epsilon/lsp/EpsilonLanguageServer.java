package org.eclipse.epsilon.lsp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageIssueException;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.beust.jcommander.JCommander;

public class EpsilonLanguageServer implements LanguageServer {

    protected EPackageRegistryManager ePackageRegistryManager = new EPackageRegistryManager();
    protected WorkspaceService workspaceService = new EpsilonWorkspaceService(this);
    protected TextDocumentService textDocumentService = new EpsilonTextDocumentService(this);
    protected LanguageClient client;
    
    public static void main(String[] args) throws Exception {

        CommandLineParameters commandLineParameters = new CommandLineParameters();
        JCommander.newBuilder().addObject(commandLineParameters).build().parse(args);
        System.out.println("Epsilon LSP Server started on port " + commandLineParameters.port);
        
        EpsilonLanguageServer languageServer = new EpsilonLanguageServer();

        Function<MessageConsumer, MessageConsumer> wrapper = consumer -> {
            MessageConsumer result = new MessageConsumer() {

                @Override
                public void consume(Message message) throws MessageIssueException, JsonRpcException {
                    System.out.println(message);
                    consumer.consume(message);
                }
            };
            return result;
        };

        Launcher<LanguageClient> launcher = createSocketLauncher(languageServer,
                LanguageClient.class, new InetSocketAddress("localhost", commandLineParameters.port),
                Executors.newCachedThreadPool(), wrapper);

        languageServer.connect(launcher.getRemoteProxy());
        Future<?> future = launcher.startListening();

        
        while (!future.isDone()) {
            Thread.sleep(30_000l);
        }
    }

    private void connect(LanguageClient remoteProxy) {
        this.client = remoteProxy;
    }

    static <T> Launcher<T> createSocketLauncher(Object localService, Class<T> remoteInterface,
            SocketAddress socketAddress,
            ExecutorService executorService, Function<MessageConsumer, MessageConsumer> wrapper) throws IOException {

        try (AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open()
                .bind(socketAddress)) {
            AsynchronousSocketChannel socketChannel = serverSocket.accept().get();
            return Launcher.createIoLauncher(localService, remoteInterface, Channels.newInputStream(socketChannel),
                    Channels.newOutputStream(socketChannel), executorService, wrapper);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        if (params.getWorkspaceFolders() != null) ePackageRegistryManager.initialize(params.getWorkspaceFolders());
        final InitializeResult res = new InitializeResult(new ServerCapabilities());
		res.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        res.getCapabilities().setDefinitionProvider(true);
		return CompletableFuture.supplyAsync(() -> res);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.supplyAsync(() -> Boolean.TRUE);
    }

    @Override
    public void exit() {
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    public LanguageClient getClient() {
        return client;
    }

    public EPackageRegistryManager getEPackageRegistryManager() {
        return ePackageRegistryManager;
    }
}
