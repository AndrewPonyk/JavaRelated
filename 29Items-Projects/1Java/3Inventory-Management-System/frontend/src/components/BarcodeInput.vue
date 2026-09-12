<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

const emit = defineEmits<{ scan: [barcode: string] }>()
const barcode = ref('')
const cameraOpen = ref(false)
const cameraError = ref('')
const video = ref<HTMLVideoElement | null>(null)
let stream: MediaStream | null = null
let timer: number | null = null

interface Detector {
  detect(source: CanvasImageSource): Promise<Array<{ rawValue: string }>>
}

interface DetectorConstructor {
  new (options: { formats: string[] }): Detector
}

function submit() {
  const normalized = barcode.value.trim()
  if (!normalized) return
  emit('scan', normalized)
  barcode.value = ''
}

async function openCamera() {
  cameraError.value = ''
  const DetectorClass = (window as unknown as { BarcodeDetector?: DetectorConstructor })
    .BarcodeDetector
  if (!DetectorClass) {
    cameraError.value = 'Camera barcode detection is not supported by this browser.'
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
    cameraOpen.value = true
    await new Promise((resolve) => setTimeout(resolve, 0))
    if (!video.value) return
    video.value.srcObject = stream
    await video.value.play()
    const detector = new DetectorClass({
      formats: ['ean_8', 'ean_13', 'upc_a', 'code_39', 'code_128', 'qr_code'],
    })
    timer = window.setInterval(async () => {
      if (!video.value) return
      const results = await detector.detect(video.value).catch(() => [])
      if (results[0]?.rawValue) {
        emit('scan', results[0].rawValue)
        closeCamera()
      }
    }, 350)
  } catch (error) {
    cameraError.value = error instanceof Error ? error.message : 'Camera access failed.'
    closeCamera()
  }
}

function closeCamera() {
  if (timer !== null) window.clearInterval(timer)
  timer = null
  stream?.getTracks().forEach((track) => track.stop())
  stream = null
  cameraOpen.value = false
}

onBeforeUnmount(closeCamera)
</script>

<template>
  <div class="space-y-2">
    <form
      class="flex flex-col gap-2 sm:flex-row"
      aria-label="Barcode lookup"
      @submit.prevent="submit"
    >
      <label for="barcode" class="sr-only">Barcode</label>
      <input
        id="barcode"
        v-model="barcode"
        autocomplete="off"
        class="field min-w-0 flex-1"
        placeholder="Scan or enter a barcode"
      />
      <button class="btn-primary" type="submit">Find item</button>
      <button class="btn-secondary" type="button" @click="openCamera">Use camera</button>
    </form>
    <p v-if="cameraError" class="text-sm text-red-700" role="alert">{{ cameraError }}</p>
    <div v-if="cameraOpen" class="rounded-lg bg-slate-950 p-3">
      <video ref="video" class="max-h-64 w-full rounded" muted playsinline />
      <button class="mt-2 text-sm font-semibold text-white underline" @click="closeCamera">
        Close camera
      </button>
    </div>
  </div>
</template>
