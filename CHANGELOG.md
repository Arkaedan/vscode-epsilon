# Change Log

## 1.4.2

Changed Flock extension from `.flock` to `.mig`

## 1.4.1

`New EGX/EGL Pair` command now also works for files.

_Reminder: this feature is disabled by default_

## 1.4.0

Added `New EGX/EGL Pair` command to create a new EGX file and EGL file with the same name.

- This command is available in the command palette
- Additionally, the command is available in the file explorer context menu when right clicking on a directory
  - However this is disabled by default but can be enabled in the settings

## 1.3.1

Back-ticks are now an auto-closing pair in the languages that support using them for escaping keywords

## 1.3.0

Emfatic: Add support for `mapentry` keyword

Meta: Add Epsilon logo for extension icon

## 1.2.1

Fix: Make valid web extension (thanks [Dimitris](https://github.com/kolovos))

## 1.2.0

Added detection of Epsilon program locations in the terminal (thanks [Dimitris](https://github.com/kolovos))

## 1.1.2

Fix highlighting for comments in the middle of EGL static sections

## 1.1.1

Fix EGL static section matching

## 1.1.0

Improved EGL support:

- Static sections are now designated as a string for better highlighting
- Added better bracket handling for dynamic sections

## 1.0.0

Initial release.

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
