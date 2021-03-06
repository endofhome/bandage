#!/usr/bin/env bash

echo "
\`.. \`..         \`.       \`...     \`..\`.....          \`.          \`....   \`........
\`.    \`..      \`. ..     \`. \`..   \`..\`..   \`..      \`. ..      \`.    \`.. \`..
\`.     \`..    \`.  \`..    \`.. \`..  \`..\`..    \`..    \`.  \`..    \`..        \`..
\`... \`.      \`..   \`..   \`..  \`.. \`..\`..    \`..   \`..   \`..   \`..        \`......
\`.     \`..  \`...... \`..  \`..   \`. \`..\`..    \`..  \`...... \`..  \`..   \`....\`..
\`.      \`. \`..       \`.. \`..    \`. ..\`..   \`..  \`..       \`..  \`..    \`. \`..
\`.... \`.. \`..         \`..\`..      \`..\`.....    \`..         \`..  \`.....   \`........
"
echo "********************************************************************************"
echo

printf "* Compiling Kotlin code... "
./gradlew assemble &>/dev/null

if [[ $? -eq 0 ]]; then
  echo "OK"
else
  echo "Compilation error"
  exit 1
fi

./download-libs.sh
