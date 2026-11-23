import json
data = json.load(open('traced.json'))
# FolioIcons name -> (sheet cell, auto-mirror in RTL)
USED = {
 'ArrowBack':('B1.7',True),'Delete':('B11.4',False),'Edit':('B12.1',False),'Close':('B11.7',False),'Search':('B1.6',False),
 'PlayArrow':('B9.4',False),'MusicNote':('A2.6',False),'MenuBook':('B1.2',True),'Add':('B11.8',False),'Settings':('B1.5',False),
 'CheckCircle':('B9.1',False),'Bookmark':('A3.8',False),'Star':('A3.2',False),'LibraryMusic':('B9.7',False),'Extension':('B7.1',False),
 'SkipNext':('B4.8',False),'Refresh':('B7.5',False),'PlaylistAdd':('B3.3',True),'Download':('B7.7',False),'Undo':('B5.7',True),
 'Timer':('B12.6',False),'SkipPrevious':('B4.7',False),'Pause':('B9.5',False),'Lock':('B10.2',False),'Language':('B7.4',False),
 'Folder':('B12.7',False),'WarningAmber':('B9.3',False),'VisibilityOff':('B10.6',False),'Tune':('B2.8',False),'Translate':('B7.3',False),
 'Shuffle':('B12.4',False),'Share':('B12.3',False),'RemoveCircleOutline':('A2.5',False),'QueueMusic':('B9.6',True),'MoreVert':('B1.8',False),
 'Gesture':('A3.1',False),'FileDownload':('B11.5',False),'Explore':('B6.9',False),'Draw':('B5.5',False),'DragHandle':('A1.2',False),
 'CreateNewFolder':('B2.1',False),'CollectionsBookmark':('B4.6',False),'CloudOff':('B8.4',False),'ChevronRight':('B12.9',False),
 'ChevronLeft':('B12.8',False),'Check':('B11.9',False),'BookmarkBorder':('B4.5',False),'TravelExplore':('A2.1',False),'Stop':('B12.5',False),
 'StickyNote2':('B1.3',False),'StarBorder':('B12.2',False),'Sort':('B2.7',False),'SelectAll':('B8.6',False),'Restore':('B3.4',False),
 'RemoveDone':('A1.5',False),'Redo':('B5.8',True),'RadioButtonUnchecked':('B9.2',False),'Public':('B7.4',False),'NoteAdd':('B1.9',False),
 'NearMe':('A1.7',False),'MergeType':('A1.6',True),'LockOpen':('A1.4',False),'LocalFireDepartment':('B9.9',False),'List':('B2.9',True),
 'Inventory2':('B10.8',False),'Insights':('B1.4',False),'Info':('B11.3',False),'Home':('B1.1',False),'GraphicEq':('A2.3',False),
 'FlipToBack':('B8.7',False),'FilterList':('B2.6',False),'FavoriteBorder':('B3.1',False),'Favorite':('B3.2',False),
 'DownloadForOffline':('B7.9',False),'DownloadDone':('B7.8',False),'DoneAll':('B8.9',False),'ContentCopy':('A1.1',False),
 'BorderColor':('B5.3',False),'Bookmarks':('B4.6',False),'Book':('B6.8',False),'AutoFixNormal':('A2.2',False),
}
# Filled versions and icons for the features that are coming.
EXTRA = {
 'HomeFilled':'A3.3','MenuBookFilled':'A3.4','StickyNoteFilled':'A3.5','InsightsFilled':'A3.6','SettingsFilled':'A3.7',
 'Sun':'A4.3','Lamp':'A4.4','Bedtime':'A4.5','Hourglass':'A4.6','Clock':'A4.7','Coffee':'A5.2','Bell':'B11.1',
 'Headphones':'A6.3','Speaker':'A6.4','Dictionary':'A6.5','FormatQuote':'A6.7','Feather':'A7.1','Link':'A7.2','RssFeed':'A7.3',
 'Newspaper':'A7.4','Ruler':'A7.5','PageCurl':'A7.6','TextJustify':'A9.1','Calendar':'A9.2','Trophy':'A9.3','Candle':'A9.4',
 'Leaf':'A9.5','Dice':'A9.6','ContinueReading':'A10.1','QuoteCard':'A10.2','Airplane':'A10.3','BatteryLeaf':'A10.4',
 'Help':'A10.5','Sparkle':'A10.9','Palette':'B10.9','Fingerprint':'B10.3','Broom':'B10.5','Moon':'B5.2','Eye':'A2.7',
 'Fullscreen':'B5.1','ZoomIn':'B6.1','ZoomOut':'B6.2','FontSize':'B6.4','LineHeight':'B6.5','Tag':'B2.4','GridView':'B2.5',
 'Pdf':'B3.5','Image':'B3.9','Description':'B4.1','Toc':'B4.4','Layers':'B7.2','Target':'B10.1','Tomato':'B9.8',
 'Bookshelf':'B2.3','Upload':'A6.9','Shield':'B7.6',
}
lines = []
lines.append('''package app.folio.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Folio's own icon set: soft, rounded line icons traced from the app's icon sheets
 * (docs/icons). Every icon is a single colour, so it takes the tint of whatever theme is on,
 * exactly like the Material icons it replaces. Built on first use, then kept.
 *
 * The names follow the Material icons they replaced, so a screen reads the same as before.
 */
object FolioIcons {''')
paths = {}
def prop(name, key, mirror, extra=''):
    paths[key] = data[key]
    const = 'P_' + key.replace('.', '_')
    lines.append(f'    val {name}: ImageVector by lazy {{ icon("{name}", {const}{", autoMirror = true" if mirror else ""}{extra}) }}')
for name,(key,mirror) in USED.items():
    prop(name, key, mirror, ', slashed = true' if name == 'RemoveDone' else '')
lines.append('')
lines.append('    // Filled versions, and icons for features still to come.')
for name,key in EXTRA.items():
    prop(name, key, False)
lines.append('}')
lines.append('''
private fun icon(name: String, path: String, autoMirror: Boolean = false, slashed: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror)
        .addPath(addPathNodes(path), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        .apply {
            if (slashed) {
                addPath(
                    addPathNodes("M4.5 4.5L19.5 19.5"),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                )
            }
        }
        .build()
''')
for key in sorted(paths):
    lines.append(f'private const val P_{key.replace(".", "_")} = "{paths[key]}"')
open('../../app/src/main/java/app/folio/ui/icons/FolioIcons.kt','w').write('\n'.join(lines)+'\n')
print(len(USED), 'replacements,', len(EXTRA), 'extra,', len(paths), 'distinct paths')
json.dump(USED, open('used.json','w'))
