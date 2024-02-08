import * as vscode from "vscode";
import { TextEncoder } from "util";

export function registerTemplateHelperCommands(
  context: vscode.ExtensionContext
) {
  const subscriptions = context.subscriptions;

  subscriptions.push(
    vscode.commands.registerCommand(
      "epsilon.newEgxEglPair",
      (...args: any[]) => {
        let path: string | undefined = undefined;

        // If the command was invoked from the explorer context menu,
        // the first argument should be the selected file/folder
        if (args.length > 0 && args[0].hasOwnProperty("path")) {
          path = args[0].path;
        }

        createNewEgxEglPair(path);
      }
    )
  );
}

async function createNewEgxEglPair(path: string | undefined): Promise<void> {
  if (path === undefined) {
    const prefilledPath =
      vscode.workspace.workspaceFolders?.[0].uri.fsPath ?? "";
    path = await vscode.window.showInputBox({
      title: "Path",
      prompt: "Enter the path where the new files should be created",
      value: prefilledPath,
      valueSelection: [prefilledPath.length, prefilledPath.length],
    });
    if (path?.endsWith("/") || path?.endsWith("\\")) {
      path = path.slice(0, -1);
    }
  }

  if (path === undefined) {
    vscode.window.showInformationMessage("No path was provided");
    return;
  }

  let fileName: string | undefined = undefined;
  let eglContent = new Uint8Array();
  let egxPath: string = "";
  let eglPath: string = "";

  // Check if path is a file
  const pathUri = vscode.Uri.file(path);
  const fileStat = await vscode.workspace.fs.stat(pathUri);
  if (fileStat.type === vscode.FileType.File) {
    fileName = path.split(/[\\\/]/).at(-1);
    eglContent = await vscode.workspace.fs.readFile(pathUri);
    egxPath = path + ".egx";
    eglPath = path + ".egl";
  } else {
    fileName = await vscode.window.showInputBox({
      title: "File Name",
      prompt: "Enter the name of the new files",
    });
    egxPath = path + "/" + fileName + ".egx";
    eglPath = path + "/" + fileName + ".egl";
  }

  if (fileName === undefined) {
    vscode.window.showErrorMessage("No file name was provided");
    return;
  }

  const egxUri = vscode.Uri.file(egxPath);
  const eglUri = vscode.Uri.file(eglPath);

  const egxContentString = getEgxRule(fileName);
  const egxContent = new TextEncoder().encode(egxContentString);
  await vscode.workspace.fs.writeFile(egxUri, egxContent);
  await vscode.workspace.fs.writeFile(eglUri, eglContent);
}

function getEgxRule(fileName: string): string {
  const ruleName = fileName.replace(/[^a-zA-Z0-9]/g, "_");
  return `rule ${ruleName} {\n\ttemplate: '${fileName}.egl'\n\ttarget: '${fileName}'\n}\n`;
}
