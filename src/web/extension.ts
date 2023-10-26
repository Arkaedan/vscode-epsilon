import * as vscode from "vscode";

import { registerTerminalLinkProvider } from "../common/terminal-link-provider";
import { registerTemplateHelperCommands } from "../common/template-helpers";

export function activate(context: vscode.ExtensionContext) {
  registerTerminalLinkProvider(context);
  registerTemplateHelperCommands(context);
}

// this method is called when your extension is deactivated
export function deactivate() {}
