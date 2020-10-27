mkdir "data"
echo "Hello" > "data/regular-file.txt"
mkfifo "data/fifo-named-pipe"

RESULT=$("${SHADOW_COPY_SH}" "data")

[[ -d "data/.shadow-copy" ]]
[[ -d "$RESULT" ]] || (echo "'$RESULT' directory should exist" && false)
[[ -f "$RESULT/regular-file.txt" ]] || (echo "Not a regular file: $RESULT/regular-file.txt" && false)
[[ -f "$RESULT/fifo-named-pipe" ]] || (echo "Not a regular file: $RESULT/fifo-named-pipe" && false)

[[ $(sed -r 's|^Hello$|OK|' "$RESULT/regular-file.txt") == "OK"  ]] || (cat "$RESULT/regular-file.txt" && false)
[[ $(sed -r "s|^unsupported 'named pipe' file type, time of last data modification: [0-9]{4}.*$|OK|" "$RESULT/fifo-named-pipe") == "OK"  ]] || (cat "$RESULT/fifo-named-pipe" && false)
