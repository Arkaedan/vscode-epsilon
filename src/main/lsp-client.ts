import { workspace, ExtensionContext, window } from "vscode";
import * as net from "net";
import {
  LanguageClient,
  LanguageClientOptions,
  StreamInfo,
  integer,
} from "vscode-languageclient/node";
import { Trace } from "vscode-jsonrpc";
import { ChildProcessWithoutNullStreams, spawn } from "child_process";

let client: LanguageClient;
let languageServerProcess: ChildProcessWithoutNullStreams;
let languageServerStarted = false;
let debugLanguageServer = false;

export function activate(context: ExtensionContext) {
  // Find a free port and start the language server
  const server = net.createServer();
  server.listen(0 /* any available port */, () => {
    let port = (server.address() as net.AddressInfo).port;
    startLanguageServer(port, context);
    server.close();
  });
}

export async function deactivate() {
  if (client) {
    await client.stop();
  }
  if (languageServerProcess) {
    languageServerProcess.kill();
  }
}

function startLanguageServer(port: integer, context: ExtensionContext) {
  // Check if java is installed and at least version 11
  checkJavaVersionInstalled(11);

  // If the debug flag is set to true, we expect that the language server already runs in port 5007
  if (debugLanguageServer) {
    window.showInformationMessage(
      "Debugging mode enabled. The language server is expected to be running on port 5007.");
    port = 5007;
    startLanguageClient(port);
  }
  // otherwise we launch it from its jar file
  else {
    const jarPath = context.asAbsolutePath(
      "language-server/target/language-server.jar"
    );
    languageServerProcess = spawn("java", ["-jar", jarPath, "-p", port + ""]);
    languageServerProcess.stdout?.on("data", (data) => {
      startLanguageClient(port);
    });
  }
}

function startLanguageClient(port: integer) {
    // The first time the server produces some text in its standard output
    // it means that it is alive and ready to accept connections
    if (languageServerStarted) {
      return;
    } else {
      languageServerStarted = true;
    }

    let serverOptions = () => {
      // Connect to language server via socket
      let socket = net.connect({ port: port });
      let result: StreamInfo = {
        writer: socket,
        reader: socket,
      };
      return Promise.resolve(result);
    };

    let clientOptions: LanguageClientOptions = {
      documentSelector: [{ scheme: "file" }],
      synchronize: {
        fileEvents: [
          workspace.createFileSystemWatcher("**/*.eol"),
          workspace.createFileSystemWatcher("**/*.evl"),
          workspace.createFileSystemWatcher("**/*.etl"),
          workspace.createFileSystemWatcher("**/*.egl"),
          workspace.createFileSystemWatcher("**/*.egx"),
          workspace.createFileSystemWatcher("**/*.ecl"),
          workspace.createFileSystemWatcher("**/*.eml"),
          workspace.createFileSystemWatcher("**/*.mig"),
          workspace.createFileSystemWatcher("**/*.pinset"),
          workspace.createFileSystemWatcher("**/*.epl"),
          workspace.createFileSystemWatcher("**/*.flexmi"),
          workspace.createFileSystemWatcher("**/*.emf"),
        ],
      },
    };

    client = new LanguageClient(
      "epsilon",
      "Epsilon Editor",
      serverOptions,
      clientOptions
    );
    client.setTrace(Trace.Verbose);
    client.start();
}

function checkJavaVersionInstalled(requiredVersion: number) {
  const javaVersionProcess = spawn("java", ["-version"]);
  javaVersionProcess.stderr.on("data", (data) => {
    const javaVersionString = data.toString();
    const javaVersionNumber = javaVersionString
      .split("\n")[0]
      .split(" ")[2]
      .replace(/"/g, "");
    const javaVersionNumberMajor = parseInt(javaVersionNumber.split(".")[0]);
    if (javaVersionNumberMajor < requiredVersion) {
      window.showErrorMessage(
        "Java 11 or later is required to run the Epsilon language server. Please install Java 11 or later and reload the window."
      );
    } else {
      window.showInformationMessage(
        `Java ${javaVersionNumberMajor} detected. The Epsilon language server will be started.`
      );
    }
  });
}
