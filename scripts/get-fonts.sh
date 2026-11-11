#!/usr/bin/env bash
# Downloads the app's fonts from the Google Fonts repository (SIL Open Font License)
# into app/src/main/res/font. Run by the GitHub build before compiling.
set -euo pipefail

dest="app/src/main/res/font"
mkdir -p "$dest"
base="https://raw.githubusercontent.com/google/fonts/main/ofl"

fetch() {
  local path="$1" name="$2"
  if [ ! -s "$dest/$name" ]; then
    curl -fsSL --retry 4 --retry-delay 3 -o "$dest/$name" "$base/$path"
  fi
  echo "ok  $name"
}

fetch "montserrat/Montserrat%5Bwght%5D.ttf"            montserrat.ttf
fetch "oswald/Oswald%5Bwght%5D.ttf"                    oswald.ttf
fetch "bebasneue/BebasNeue-Regular.ttf"                bebas_neue.ttf
fetch "comfortaa/Comfortaa%5Bwght%5D.ttf"              comfortaa.ttf
fetch "playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf"  playfair_display.ttf
fetch "lora/Lora%5Bwght%5D.ttf"                        lora.ttf
fetch "abrilfatface/AbrilFatface-Regular.ttf"          abril_fatface.ttf
fetch "lobster/Lobster-Regular.ttf"                    lobster.ttf
fetch "pacifico/Pacifico-Regular.ttf"                  pacifico.ttf
fetch "dancingscript/DancingScript%5Bwght%5D.ttf"      dancing_script.ttf
fetch "caveat/Caveat%5Bwght%5D.ttf"                    caveat.ttf
fetch "greatvibes/GreatVibes-Regular.ttf"              great_vibes.ttf
