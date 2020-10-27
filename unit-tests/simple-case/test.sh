cd "data"
RESULT=$("${SHADOW_COPY_SH}")

[[ -d ".shadow-copy" ]]

# return only one line
(( $(echo "$RESULT" | wc -l) == 1 )) || (echo "$RESULT" && false)

# $RESULT should match something like: .shadow-copy/2020.10.27-10h29-1
[[ $(echo "$RESULT" | sed -r 's|^.shadow-copy/[0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]{2}h[0-9]{2}-[0-9]+$|OK|') == "OK"  ]] || (echo "$RESULT" && false)

[[ -d "$RESULT" ]] || (echo "'$RESULT' directory should exist" && false)
[[ -d "$RESULT/dir1" ]]
[[ -f "$RESULT/dir1/f2.txt" ]]
[[ -f "$RESULT/f1.txt" ]]
[[ -L "$RESULT/link1" ]]
