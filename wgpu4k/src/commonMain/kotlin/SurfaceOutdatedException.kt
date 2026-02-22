package io.ygdrasil.webgpu

/**
 * Thrown when [Surface.getCurrentTexture] is called while the surface has become outdated
 * (e.g. after a window resize or display-mode change on Metal/Vulkan).
 *
 * Extends [IllegalStateException] so existing catch blocks written against
 * [IllegalStateException] continue to work during migration to this typed exception.
 * Callers should prefer catching [SurfaceOutdatedException] directly to avoid masking
 * unrelated illegal-state errors.
 *
 * **Recovery:** reconfigure the surface with [Surface.configure] and skip the current frame;
 * the next draw call will succeed once the surface is up-to-date.
 */
class SurfaceOutdatedException(message: String) : IllegalStateException(message)
