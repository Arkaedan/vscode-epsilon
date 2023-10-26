import * as vscode from "vscode";

import { registerTerminalLinkProvider } from "../common/terminal-link-provider";
import { registerTemplateHelperCommands } from "../common/template-helpers";
import * as lspClient from "../main/lsp-client";

export function activate(context: vscode.ExtensionContext) {
  lspClient.activate(context);
  registerTerminalLinkProvider(context);
  registerTemplateHelperCommands(context);
}

// this method is called when your extension is deactivated
export function deactivate() {
  lspClient.deactivate();
}
