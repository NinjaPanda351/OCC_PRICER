# UI refresh

## Design and behavior

OCC Midnight uses charcoal backgrounds, slightly lighter panels, subdued blue accents and consistent typography. Icons and the overview card artwork are drawn with Java2D and scale with the display.

- **Overview:** working shortcuts for a new trade, trade history, set pricing, inventory and files.
- **Navigation:** persistent sidebar and active-screen indicator. Narrow windows use icons with tooltips and accessible names. The menu button expands or collapses navigation. Preferences, Help and the version/About button remain accessible.
- **Trades:** card entry appears first, followed by payout selection and customer details. Search, paste, undo, vintage codes, selection actions, retry and save/export remain available. Escape closes the Vintage Codes and Help windows.
- **Compact windows:** short trade windows use inline totals and a Customer details toggle. Entered customer information survives collapsing and resizing; choosing check/split tender expands the details. The form scrolls when needed and the trade table uses the remaining height. Primary entry/save controls remain visible at the tested minimum workspace sizes.
- **Tables:** columns keep readable minimum widths, scroll horizontally when necessary and fill larger viewports. The set selector and receipt preview retain adjustable split panes.
- **Settings and toolbars:** settings pages scroll vertically, action rows wrap, and button sizes follow their labels. File action text no longer overlaps buttons.
- **Window placement:** size, position and maximized state persist on close. Restored windows fit available monitor space, including taskbars and disconnected-monitor fallback.
- **Inventory and files:** clear empty states and consistent table styling.
- **Appearance:** theme selection persists; light and the existing optional themes remain available. OCC Midnight replaces the old default dark choice once during migration.

Pricing, settlement, draft recovery, exports and keyboard shortcuts continue through the existing services and actions.

## Visual verification

The images in [ui](ui/) are headless renders of the actual application components. The trade examples use synthetic card prices and isolated application data; they are not live quotes. Desktop title bars, native file dialogs and physical printing are outside these previews.

The render utility exercises dashboard navigation, empty and populated trades, split tender, compact layouts, inventory, set pricing, files/history, every preferences tab and a switch to light mode. Workspace captures span 960×600 through 1920×1080, including a 1280×680 laptop workspace. Additional renders use FlatLaf's 150% UI scale. These are component renders, not physical monitor or operating-system scaling tests.

Six regression checks exercise actual Swing layouts: entry/save visibility and retained trade data through repeated resizes, expandable navigation with preserved names, table scrolling/filling, access to settings below the fold, and window placement on smaller or multiple monitors. Reproduce previews from the repository root after `mvnw.cmd verify`:

```powershell
& "$env:JAVA_HOME\bin\java.exe" --enable-native-access=ALL-UNNAMED `
  '-Djava.awt.headless=true' '-Dflatlaf.uiScale=1' `
  '-Djava.util.prefs.PreferencesFactory=com.cardpricer.testsupport.MemoryPreferencesFactory' `
  '-Dcardpricer.dataDir=target/ui-preview-data' `
  -cp 'target/test-classes;target/OCC_Trade_Pricer.jar' `
  com.cardpricer.gui.UiPreview docs/ui
```

Current build evidence is stored in `audit/responsive-verification.txt` and `audit/responsive-package-verification.txt`. The Windows ZIP is rebuilt by `build.ps1`; its current payload and hashes are recorded in `audit/package-manifest.json`.
