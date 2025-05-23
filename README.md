# Eclipse Epsilon Languages Extension

Syntax highlighting and basic language server for the Eclipse Epsilon languages and Emfatic.

Initially adapted from the Epsilon extension for Sublime in the Epsilon Labs project: [https://github.com/epsilonlabs/sublime](https://github.com/epsilonlabs/sublime)

## Features

Support is included for the following languages:

- Epsilon Object Language (EOL)
- Epsilon Comparison Language (ECL)
- Epsilon Generation Language (EGL)
- EGL Co-Ordination Language (EGX)
- Epsilon Validation Language (EVL)
- Epsilon Transformation Language (ETL)
- Epsilon Merging Language (EML)
- Epsilon Pattern Language (EPL)
- Epsilon Flock
- Epsilon Pinset
- Flexmi (XML)
- Flexmi (YAML)
- Emfatic

## For Extension Developers

To package the extension into a `.vsix`:

- Run `mvn package` in the "language-server" folder to generate `language-server/target/language-server.jar`
- Run `npm run compile` to generate `out/main/extension.js` and `out/web/extension.js`
- Run `vsce package` to generate the `.vsix`

To test the `.vsix` you can use a separate instance of [Visual Studio Code Insiders](https://code.visualstudio.com/insiders/). You can either drag and drop the `.vsix` file in the extensions view or use the `Install from VSIX...` command.
