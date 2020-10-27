cd "data"

echo "Content1" > "f2.txt"
RESULT1=$("${SHADOW_COPY_SH}")

echo "Content2" > "f2.txt"
RESULT2=$("${SHADOW_COPY_SH}")

[[ -d ".shadow-copy" ]]
[[ -d "$RESULT1" ]] || (echo "'$RESULT1' directory should exist" && false)
[[ -d "$RESULT2" ]] || (echo "'$RESULT2' directory should exist" && false)

[[ "$RESULT1" != "$RESULT2" ]] || (echo "ERROR same copy: '$RESULT1' '$RESULT2'" && false)

[[ -f "$RESULT1/f1.txt" ]]
[[ -f "$RESULT2/f1.txt" ]]

INODE1="$(stat -c '%i' "$RESULT1/f1.txt")"
INODE2="$(stat -c '%i' "$RESULT2/f1.txt")"
[[ "$INODE1" == "$INODE2" ]] || (echo "ERROR missing hardlink for: '$RESULT2/f1.txt'" && false)

INODE1="$(stat -c '%i' "$RESULT1/f2.txt")"
INODE2="$(stat -c '%i' "$RESULT2/f2.txt")"
[[ "$INODE1" != "$INODE2" ]] || (echo "ERROR unexpected hardlink for: '$RESULT2/f2.txt'" && false)
