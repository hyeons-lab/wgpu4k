@file:OptIn(ExperimentalForeignApi::class)

package io.ygdrasil.webgpu

import cnames.structs.GLFWwindow
import ffi.NativeAddress
import glfw.GLFW_CLIENT_API
import glfw.GLFW_FALSE
import glfw.GLFW_NO_API
import glfw.GLFW_RESIZABLE
import glfw.GLFW_VISIBLE
import glfw.glfwCreateWindow
import glfw.glfwDestroyWindow
import glfw.glfwInit
import glfw.glfwWindowHint
import io.ygdrasil.webgpu.WGPU.Companion.createInstance
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import platform.MetalKit.MTKView

// ---------------------------------------------------------------------------
// MTKView-based renderer (for Flutter / embedded Metal views)
// ---------------------------------------------------------------------------

/**
 * Creates a [MacosContext] backed by the given [MTKView]'s Metal layer.
 * The view must already be part of the view hierarchy so that its layer is valid.
 */
suspend fun macosContextRenderer(
    view: MTKView,
    width: Int,
    height: Int,
    deferredRendering: Boolean = false,
): MacosContext {
    val layer = view.layer
    val layerPointer: COpaquePointer = interpretCPointer<COpaque>(layer.objcPtr())!!.reinterpret()
    return macosContextRendererFromLayer(NativeAddress(layerPointer), width, height, deferredRendering)
}

/**
 * Creates a [MacosContext] from a raw [CAMetalLayer] pointer [layerPtr].
 * Use this from C-API bridges where only the opaque layer address is available.
 */
suspend fun macosContextRendererFromLayer(
    layerPtr: NativeAddress,
    width: Int,
    height: Int,
    deferredRendering: Boolean = false,
): MacosContext {
    val instance = WGPU.createInstance() ?: error("Can't create WGPU instance")
    val nativeSurface = instance.getSurfaceFromMetalLayer(layerPtr) ?: error("Can't create Surface")
    val adapter = instance.requestAdapter(nativeSurface) ?: error("Can't create Adapter")
    val device = adapter.requestDevice().getOrThrow()
    val surface = Surface(nativeSurface, width.toUInt(), height.toUInt())
    nativeSurface.computeSurfaceCapabilities(adapter)
    val renderingContext = when (deferredRendering) {
        true -> TextureRenderingContext(width.toUInt(), height.toUInt(), GPUTextureFormat.RGBA8Unorm, device)
        false -> SurfaceRenderingContext(surface, surface.supportedFormats.first())
    }
    return MacosContext(WGPUContext(surface, adapter, device, renderingContext))
}

class MacosContext(val wgpuContext: WGPUContext) : AutoCloseable {
    override fun close() = wgpuContext.close()
}

// ---------------------------------------------------------------------------
// GLFW-based renderer (for the desktop demo / headless testing)
// Mirrors desktopNativeMain/glfw.kt but uses the fixed-dimension Surface.
// ---------------------------------------------------------------------------

suspend fun glfwContextRenderer(
    width: Int = 1,
    height: Int = 1,
    title: String = "",
    deferredRendering: Boolean = false,
    onUncapturedError: GPUUncapturedErrorCallback? = null,
): GLFWContext {
    glfwInit()
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
    // Disable context creation — WGPU manages its own graphics context
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    val windowHandler = glfwCreateWindow(width, height, title, null, null)
        ?: error("fail to create windows")

    val wgpu = createInstance() ?: error("fail to create WGPU instance")
    val nativeSurface = wgpu.getNativeSurface(windowHandler)
    val surface = Surface(nativeSurface, width.toUInt(), height.toUInt())

    val adapter = wgpu.requestAdapter(nativeSurface)
        ?: error("fail to get adapter")

    val device = adapter.requestDevice(
        DeviceDescriptor(onUncapturedError = onUncapturedError)
    ).getOrThrow()

    nativeSurface.computeSurfaceCapabilities(adapter)

    val renderingContext = when (deferredRendering) {
        true -> TextureRenderingContext(256u, 256u, GPUTextureFormat.RGBA8Unorm, device)
        false -> SurfaceRenderingContext(surface, surface.supportedFormats.first())
    }

    return GLFWContext(windowHandler, WGPUContext(surface, adapter, device, renderingContext))
}

class GLFWContext(
    val windowHandler: CValuesRef<GLFWwindow>,
    val wgpuContext: WGPUContext,
) : AutoCloseable {
    override fun close() {
        wgpuContext.close()
        glfwDestroyWindow(windowHandler)
    }
}
