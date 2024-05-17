'use strict';

import * as vscode from 'vscode';

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(vscode.debug.registerDebugAdapterDescriptorFactory(
        'epsilon', new EpsilonDebugAdapterServerDescriptorFactory()));
}

export function deactivate() {
	// nothing to do
}

class EpsilonDebugAdapterServerDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {

	createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable | undefined): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
		let port = 'port' in session.configuration ? session.configuration['port'] : 4040;
		return new vscode.DebugAdapterServer(port);
	}

}
