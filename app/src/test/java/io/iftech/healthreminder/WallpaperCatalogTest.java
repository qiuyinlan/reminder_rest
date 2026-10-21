package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WallpaperCatalogTest {
    @Test public void builtInCatalogIsComplete() {
        WallpaperCatalog.Wallpaper[] builtIns = WallpaperCatalog.builtIns();
        assertEquals(6, builtIns.length);
        assertTrue(WallpaperCatalog.isBuiltIn(WallpaperCatalog.DEFAULT));
        for (WallpaperCatalog.Wallpaper wallpaper : builtIns) {
            assertEquals(wallpaper.label, WallpaperCatalog.label(wallpaper.id));
            assertTrue(WallpaperCatalog.isBuiltIn(wallpaper.id));
        }
    }

    @Test public void unknownIdsFallBackToDefault() {
        assertEquals("默认表情", WallpaperCatalog.label(null));
        assertEquals("默认表情", WallpaperCatalog.label("not-a-wallpaper"));
    }
}
