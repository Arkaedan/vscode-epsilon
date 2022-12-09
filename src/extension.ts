import * as vscode from 'vscode';

export function activate(context: vscode.ExtensionContext) {

	// This line of code will only be executed once when your extension is activated
	// console.log('Congratulations, your extension "vscode-epsilon" is now active!');

	const subscriptions = context.subscriptions;

	subscriptions.push(vscode.window.registerTerminalLinkProvider({
		provideTerminalLinks: (context, token) => {
			
			// Regular expression that matches Epsilon program locations
			let regexp = /\(((.*?)@(\d*):(\d*)-(\d*):(\d*))\)/i;

			// Match the line against the regexp
			let matches = context.line.match(regexp);

			if (matches !== null) {
				let startIndex = context.line.indexOf(matches[1]);
				let length = matches[1].length;
				let data = {
					file: matches[2], 
					startLine: parseInt(matches[3]),
					startColumn: parseInt(matches[4]),
					endLine: parseInt(matches[5]),
					endColumn: parseInt(matches[6])
				};
				return [
					{
						startIndex,
						length: length,
						data: data
					}
				];
			}
			else {
				return [];
			}
		},
		handleTerminalLink: (link: any) => {

			// Open an editor for the file
			vscode.workspace.openTextDocument(link.data.file).then(document => 
				// Show the editor
				vscode.window.showTextDocument(document)).then(x => {
					let activeEditor = vscode.window.activeTextEditor;
					if (activeEditor !== undefined) {
						// Find the range to reveal
						let range = new vscode.Range(new vscode.Position(link.data.startLine - 1, link.data.startColumn), new vscode.Position(link.data.endLine - 1, link.data.endColumn));
						// Reveal the range
						activeEditor.selection = new vscode.Selection(range.start, range.end);
						activeEditor.revealRange(range);
					}
				});
			},
		}
	));

	subscriptions.push(vscode.commands.registerCommand('epsilon.newEgxEglPair', (...args: any[]) => {
		let folderPath: string | undefined = undefined;

		// If the command was invoked from the explorer context menu,
		// the first argument should be the selected folder
		if (args.length > 0 && args[0].hasOwnProperty('path')) {
			folderPath = args[0].path;
		}
		
		createNewEgxEglPair(folderPath);
	}));
}

// this method is called when your extension is deactivated
export function deactivate() {}

async function createNewEgxEglPair(folderPath: string | undefined): Promise<void> {
	if (folderPath === undefined) {
		const prefilledPath = vscode.workspace.workspaceFolders?.[0].uri.fsPath ?? '';
		folderPath = await vscode.window.showInputBox({
			title: 'Folder Path',
			prompt: 'Enter the path to the folder where the new files should be created',
			value: prefilledPath,
			valueSelection: [prefilledPath.length, prefilledPath.length],
		});
	}

	if (folderPath === undefined) {
		vscode.window.showErrorMessage('No folder path was provided');
		return;
	}

	const fileName = await vscode.window.showInputBox({
		title: 'File Name',
		prompt: 'Enter the name of the new files (not including .egx or .egl)',
	});

	if (fileName === undefined) {
		vscode.window.showErrorMessage('No file name was provided');
		return;
	}

	const egxPath = folderPath + '/' + fileName + '.egx';
	const eglPath = folderPath + '/' + fileName + '.egl';
	const egxUri = vscode.Uri.file(egxPath);
	const eglUri = vscode.Uri.file(eglPath);
	await vscode.workspace.fs.writeFile(egxUri, new Uint8Array());
	await vscode.workspace.fs.writeFile(eglUri, new Uint8Array());
}
