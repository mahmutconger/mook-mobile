#!/usr/bin/env bash
# Cloud Functions'ı küçük gruplar halinde deploy eder.
#
# Neden: Tek seferde ~35 fonksiyon deploy edilince her yeni revizyon aynı anda bir
# örnek başlatır ve proje, bölge başına CPU kotasını ("Quota exceeded for total
# allowable CPU per project per region") aşar. Gruplar arasında bekleyerek eski
# revizyonların kapanmasına zaman tanınır.
#
# Kullanım:
#   scripts/deploy_functions_batched.sh                 # son deploy'da başarısız olan 14 fonksiyon
#   scripts/deploy_functions_batched.sh swipe getUsage  # yalnızca verilen fonksiyonlar
# Ortam değişkenleri: BATCH_SIZE (varsayılan 4), PAUSE_SECONDS (varsayılan 90)
set -uo pipefail

PROJECT_ID="walktalk-1123f"
BATCH_SIZE="${BATCH_SIZE:-4}"
PAUSE_SECONDS="${PAUSE_SECONDS:-90}"

if [ "$#" -gt 0 ]; then
  functionNames=("$@")
else
  functionNames=(
    activateMookProfile onReportCreated closeRoomSlots recordDeviceTrialConsumption
    moderateUser revenueCatWebhook admobRewardedSsv bootstrapMonetization
    rewind recordProfileVisit switchRoom updateTimeZone
    generateSsoToken deleteMessage
  )
fi

cd "$(dirname "$0")/.." || exit 1
failedBatches=()
total=${#functionNames[@]}

for ((start = 0; start < total; start += BATCH_SIZE)); do
  batch=("${functionNames[@]:start:BATCH_SIZE}")
  only=$(printf 'functions:%s,' "${batch[@]}"); only="${only%,}"
  echo ""
  echo "=== Grup $((start / BATCH_SIZE + 1)): ${batch[*]}"

  if ! firebase deploy --project "$PROJECT_ID" --only "$only"; then
    echo "!!! Grup başarısız; ${PAUSE_SECONDS} sn bekleyip bir kez daha deneniyor..."
    sleep "$PAUSE_SECONDS"
    if ! firebase deploy --project "$PROJECT_ID" --only "$only"; then
      failedBatches+=("${batch[*]}")
    fi
  fi

  if (( start + BATCH_SIZE < total )); then
    echo "--- Kotanın boşalması için ${PAUSE_SECONDS} sn bekleniyor..."
    sleep "$PAUSE_SECONDS"
  fi
done

echo ""
if [ "${#failedBatches[@]}" -eq 0 ]; then
  echo "✔ Tüm gruplar başarıyla deploy edildi."
else
  echo "✖ Başarısız kalan gruplar (tekrar denemek için bu adları betiğe argüman olarak ver):"
  printf '  %s\n' "${failedBatches[@]}"
  exit 1
fi
