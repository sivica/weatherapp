# Vedro — Clear sky

The approved direction is a yellow sun behind a white cloud on a clear blue
background. `vedro-icon.svg` is the editable geometric source; the original
comparison board and its text are not app assets.

`GenerateIcons.java` produces the five legacy launcher densities, round icons,
adaptive vector foreground/background, a separate monochrome themed silhouette,
and the 512 × 512 RGBA Play icon. The foreground fits the centered 66 dp safe
zone of a 108 dp layer. Play's icon is full-bleed square; Play applies its own mask.

Run from the repository root with JDK 21 (previews go outside tracked content):

```sh
java -Djava.awt.headless=true branding/GenerateIcons.java . /tmp/vedro-icon-previews
```

The package and signing identity remain unchanged. Icon/theme colors do not
introduce a new screen design, data feature or permission.
