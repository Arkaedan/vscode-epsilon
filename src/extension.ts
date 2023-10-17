import { TextEncoder } from 'util';
import * as vscode from 'vscode';
import { ChildProcessWithoutNullStreams, spawn } from 'child_process';
import * as net from 'net';

import { Trace } from 'vscode-jsonrpc';
import { workspace, ExtensionContext } from 'vscode';
import { LanguageClient, LanguageClientOptions, StreamInfo, integer} from 'vscode-languageclient/node';
import { start } from 'repl';

let client: LanguageClient;
let languageServerProcess: ChildProcessWithoutNullStreams;
let languageServerStarted = false;

export function activate(context: vscode.ExtensionContext) {
    
	// Find a free port and start the language server
	const server = net.createServer();
        server.listen(0 /* any available port */, () => { 
			let port = (server.address() as net.AddressInfo).port;
			startLanguageServer(port);
			server.close();
	});

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
		let path: string | undefined = undefined;

		// If the command was invoked from the explorer context menu,
		// the first argument should be the selected file/folder
		if (args.length > 0 && args[0].hasOwnProperty('path')) {
			path = args[0].path;
		}
		
		createNewEgxEglPair(path);
	}));
}

// this method is called when your extension is deactivated
export function deactivate() {
	if (!client) {
		return undefined;
	}
	return client.stop();
	languageServerProcess.kill();
}

async function createNewEgxEglPair(path: string | undefined): Promise<void> {
	if (path === undefined) {
		const prefilledPath = vscode.workspace.workspaceFolders?.[0].uri.fsPath ?? '';
		path = await vscode.window.showInputBox({
			title: 'Path',
			prompt: 'Enter the path where the new files should be created',
			value: prefilledPath,
			valueSelection: [prefilledPath.length, prefilledPath.length],
		});
		if (path?.endsWith('/') || path?.endsWith('\\')) {
			path = path.slice(0, -1);
		}
	}

	if (path === undefined) {
		vscode.window.showInformationMessage('No path was provided');
		return;
	}

	let fileName: string | undefined = undefined;
	let eglContent = new Uint8Array();
	let egxPath: string = '';
	let eglPath: string = '';

	// Check if path is a file
	const pathUri = vscode.Uri.file(path);
	const fileStat = await vscode.workspace.fs.stat(pathUri);
	if (fileStat.type === vscode.FileType.File) {
		fileName = path.split(/[\\\/]/).at(-1);
		eglContent = await vscode.workspace.fs.readFile(pathUri);
		egxPath = path + '.egx';
		eglPath = path + '.egl';
	} else {
		fileName = await vscode.window.showInputBox({
			title: 'File Name',
			prompt: 'Enter the name of the new files',
		});
		egxPath = path + '/' + fileName + '.egx';
		eglPath = path + '/' + fileName + '.egl';
	}

	if (fileName === undefined) {
		vscode.window.showErrorMessage('No file name was provided');
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
	const ruleName = fileName.replace(/[^a-zA-Z0-9]/g, '_');
	return `rule ${ruleName} {\n\ttemplate: '${fileName}.egl'\n\ttarget: '${fileName}'\n}\n`;
}

function startLanguageServer(port : integer) {
	//port = 56295;
	console.log("Starting the Epsilon Language Server on port "+ port);

	languageServerProcess = spawn("java", ["-jar", "/Users/dk135/git/vscode-epsilon/language-server/target/language-server.jar", "-p", port + ""]);
	languageServerProcess.stdout?.on('data', data => {
		
		// The first time the server produces some text in its standard output
		// it means that it is alive and ready to accept connections
		if (languageServerStarted) { return; }
		else { languageServerStarted = true; }

		let serverOptions = () => {
			// Connect to language server via socket
			let socket = net.connect({port : port});
			let result: StreamInfo = {
				writer: socket,
				reader: socket
			};
			return Promise.resolve(result);
		};
	
		let clientOptions: LanguageClientOptions = {
			documentSelector: [ { scheme: 'file' } ],
			synchronize: {
				fileEvents: [
					workspace.createFileSystemWatcher('**/*.eol'),
					workspace.createFileSystemWatcher('**/*.evl'),
					workspace.createFileSystemWatcher('**/*.etl'),
					workspace.createFileSystemWatcher('**/*.egl'),
					workspace.createFileSystemWatcher('**/*.egx'),
					workspace.createFileSystemWatcher('**/*.ecl'),
					workspace.createFileSystemWatcher('**/*.eml'),
					workspace.createFileSystemWatcher('**/*.mig'),
					workspace.createFileSystemWatcher('**/*.pinset'),
					workspace.createFileSystemWatcher('**/*.epl')
				]
			},
		};
	
		client = new LanguageClient('epsilon', 'Epsilon Editor', serverOptions, clientOptions);
		client.setTrace(Trace.Verbose);
		client.start();
	});
	
}