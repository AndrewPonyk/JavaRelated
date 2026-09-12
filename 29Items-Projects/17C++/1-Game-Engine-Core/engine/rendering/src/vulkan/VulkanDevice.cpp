#include "engine/rendering/vulkan/VulkanDevice.hpp"

/// @file VulkanDevice.cpp
/// @brief Offscreen Vulkan renderer. Compiled only when ENGINE_HAS_VULKAN is set.
///
/// Renders into an offscreen R8G8B8A8 color target (no surface/swapchain) so it can
/// produce pixels headlessly; readColorPixels() copies the result back to the CPU.
///
/// IMPORTANT: written against the standard Vulkan 1.0 API but NOT yet compiled or run
/// (the authoring environment has the Vulkan runtime but no SDK headers/glslc). Treat
/// as build-ready, pending a first compile against the Vulkan SDK.

#if defined(ENGINE_HAS_VULKAN)

    #include "engine/core/Log.hpp"
    #include "engine/rendering/vulkan/VulkanRenderer.hpp"

    #include <array>
    #include <cstring>
    #include <string>
    #include <vector>

    #include <vulkan/vulkan.h>

namespace engine::rhi::vulkan {
namespace {

const char* vkResultName(VkResult r) {
    switch (r) {
        case VK_SUCCESS: return "VK_SUCCESS";
        case VK_ERROR_OUT_OF_HOST_MEMORY: return "VK_ERROR_OUT_OF_HOST_MEMORY";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "VK_ERROR_OUT_OF_DEVICE_MEMORY";
        case VK_ERROR_INITIALIZATION_FAILED: return "VK_ERROR_INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST: return "VK_ERROR_DEVICE_LOST";
        case VK_ERROR_EXTENSION_NOT_PRESENT: return "VK_ERROR_EXTENSION_NOT_PRESENT";
        case VK_ERROR_FEATURE_NOT_PRESENT: return "VK_ERROR_FEATURE_NOT_PRESENT";
        default: return "VK_ERROR_<other>";
    }
}

u32 findMemoryType(VkPhysicalDevice phys, u32 typeBits, VkMemoryPropertyFlags props) {
    VkPhysicalDeviceMemoryProperties memProps{};
    vkGetPhysicalDeviceMemoryProperties(phys, &memProps);
    for (u32 i = 0; i < memProps.memoryTypeCount; ++i) {
        if ((typeBits & (1u << i)) != 0 &&
            (memProps.memoryTypes[i].propertyFlags & props) == props) {
            return i;
        }
    }
    return UINT32_MAX;
}

} // namespace

    #define VK_TRY(expr, msg)                                                                 \
        do {                                                                                  \
            const VkResult _vr = (expr);                                                      \
            if (_vr != VK_SUCCESS) {                                                          \
                return err<bool>(ErrorCode::BackendError,                                     \
                                 std::string(msg) + ": " + vkResultName(_vr));                \
            }                                                                                 \
        } while (false)

// ---------------------------------------------------------------------------
// Internal state
// ---------------------------------------------------------------------------
struct VulkanDevice::Impl {
    VkInstance       instance       = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice         device         = VK_NULL_HANDLE;
    VkQueue          graphicsQueue  = VK_NULL_HANDLE;
    u32              graphicsFamily = 0;

    VkCommandPool   commandPool   = VK_NULL_HANDLE;
    VkCommandBuffer frameCmd      = VK_NULL_HANDLE;
    VkFence         frameFence    = VK_NULL_HANDLE;

    // Offscreen color target.
    VkExtent2D     extent{256, 256};
    VkFormat       colorFormat = VK_FORMAT_R8G8B8A8_UNORM;
    VkImage        colorImage  = VK_NULL_HANDLE;
    VkDeviceMemory colorMemory = VK_NULL_HANDLE;
    VkImageView    colorView   = VK_NULL_HANDLE;
    VkRenderPass   renderPass  = VK_NULL_HANDLE;
    VkFramebuffer  framebuffer = VK_NULL_HANDLE;

    // Resource tables (handle id == index).
    struct BufferRes {
        VkBuffer       buffer = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
    };
    struct PipelineRes {
        VkPipeline       pipeline = VK_NULL_HANDLE;
        VkPipelineLayout layout   = VK_NULL_HANDLE;
    };
    std::vector<BufferRes>     buffers;
    std::vector<VkShaderModule> shaders;
    std::vector<PipelineRes>   pipelines;

    VulkanCommandList commandList;
};

VulkanDevice::VulkanDevice() = default;

VulkanDevice::~VulkanDevice() {
    if (!impl_ || impl_->device == VK_NULL_HANDLE) {
        impl_.reset();
        return;
    }
    vkDeviceWaitIdle(impl_->device);

    for (auto& p : impl_->pipelines) {
        if (p.pipeline != VK_NULL_HANDLE) vkDestroyPipeline(impl_->device, p.pipeline, nullptr);
        if (p.layout != VK_NULL_HANDLE) vkDestroyPipelineLayout(impl_->device, p.layout, nullptr);
    }
    for (auto s : impl_->shaders) {
        if (s != VK_NULL_HANDLE) vkDestroyShaderModule(impl_->device, s, nullptr);
    }
    for (auto& b : impl_->buffers) {
        if (b.buffer != VK_NULL_HANDLE) vkDestroyBuffer(impl_->device, b.buffer, nullptr);
        if (b.memory != VK_NULL_HANDLE) vkFreeMemory(impl_->device, b.memory, nullptr);
    }
    if (impl_->framebuffer) vkDestroyFramebuffer(impl_->device, impl_->framebuffer, nullptr);
    if (impl_->renderPass) vkDestroyRenderPass(impl_->device, impl_->renderPass, nullptr);
    if (impl_->colorView) vkDestroyImageView(impl_->device, impl_->colorView, nullptr);
    if (impl_->colorImage) vkDestroyImage(impl_->device, impl_->colorImage, nullptr);
    if (impl_->colorMemory) vkFreeMemory(impl_->device, impl_->colorMemory, nullptr);
    if (impl_->frameFence) vkDestroyFence(impl_->device, impl_->frameFence, nullptr);
    if (impl_->commandPool) vkDestroyCommandPool(impl_->device, impl_->commandPool, nullptr);
    vkDestroyDevice(impl_->device, nullptr);
    if (impl_->instance) vkDestroyInstance(impl_->instance, nullptr);
    impl_.reset();
}

Result<bool> VulkanDevice::initialize(platform::Window* /*window*/, bool enableValidation,
                                      OffscreenConfig offscreen) {
    validation_ = enableValidation;
    impl_       = std::make_unique<Impl>();
    impl_->extent = {offscreen.width, offscreen.height};

    // ---- Instance ----
    VkApplicationInfo app{};
    app.sType              = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName   = "GameEngineCore";
    app.apiVersion         = VK_API_VERSION_1_1;

    std::vector<const char*> layers;
    if (validation_) {
        layers.push_back("VK_LAYER_KHRONOS_validation");
    }
    VkInstanceCreateInfo ici{};
    ici.sType                   = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ici.pApplicationInfo        = &app;
    ici.enabledLayerCount       = static_cast<u32>(layers.size());
    ici.ppEnabledLayerNames     = layers.empty() ? nullptr : layers.data();
    if (vkCreateInstance(&ici, nullptr, &impl_->instance) != VK_SUCCESS) {
        // Retry without validation layers (not installed on every machine).
        ici.enabledLayerCount = 0;
        ici.ppEnabledLayerNames = nullptr;
        VK_TRY(vkCreateInstance(&ici, nullptr, &impl_->instance), "vkCreateInstance");
        validation_ = false;
    }

    // ---- Physical device (first with a graphics queue) ----
    u32 count = 0;
    vkEnumeratePhysicalDevices(impl_->instance, &count, nullptr);
    if (count == 0) {
        return err<bool>(ErrorCode::BackendError, "no Vulkan physical devices");
    }
    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(impl_->instance, &count, devices.data());

    bool found = false;
    for (VkPhysicalDevice pd : devices) {
        u32 qCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(pd, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> qprops(qCount);
        vkGetPhysicalDeviceQueueFamilyProperties(pd, &qCount, qprops.data());
        for (u32 i = 0; i < qCount; ++i) {
            if ((qprops[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                impl_->physicalDevice = pd;
                impl_->graphicsFamily = i;
                found                 = true;
                break;
            }
        }
        if (found) {
            break;
        }
    }
    if (!found) {
        return err<bool>(ErrorCode::BackendError, "no graphics-capable queue family");
    }

    // ---- Logical device + queue ----
    const float priority = 1.0f;
    VkDeviceQueueCreateInfo qci{};
    qci.sType            = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qci.queueFamilyIndex = impl_->graphicsFamily;
    qci.queueCount       = 1;
    qci.pQueuePriorities = &priority;

    VkDeviceCreateInfo dci{};
    dci.sType                = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos    = &qci;
    VK_TRY(vkCreateDevice(impl_->physicalDevice, &dci, nullptr, &impl_->device),
           "vkCreateDevice");
    vkGetDeviceQueue(impl_->device, impl_->graphicsFamily, 0, &impl_->graphicsQueue);

    // ---- Command pool + frame command buffer + fence ----
    VkCommandPoolCreateInfo pci{};
    pci.sType            = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    pci.flags            = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pci.queueFamilyIndex = impl_->graphicsFamily;
    VK_TRY(vkCreateCommandPool(impl_->device, &pci, nullptr, &impl_->commandPool),
           "vkCreateCommandPool");

    VkCommandBufferAllocateInfo cbai{};
    cbai.sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cbai.commandPool        = impl_->commandPool;
    cbai.level              = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    VK_TRY(vkAllocateCommandBuffers(impl_->device, &cbai, &impl_->frameCmd),
           "vkAllocateCommandBuffers");

    VkFenceCreateInfo fci{};
    fci.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    VK_TRY(vkCreateFence(impl_->device, &fci, nullptr, &impl_->frameFence), "vkCreateFence");

    // ---- Offscreen color image ----
    VkImageCreateInfo img{};
    img.sType         = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    img.imageType     = VK_IMAGE_TYPE_2D;
    img.format        = impl_->colorFormat;
    img.extent        = {impl_->extent.width, impl_->extent.height, 1};
    img.mipLevels     = 1;
    img.arrayLayers   = 1;
    img.samples       = VK_SAMPLE_COUNT_1_BIT;
    img.tiling        = VK_IMAGE_TILING_OPTIMAL;
    img.usage         = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    img.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VK_TRY(vkCreateImage(impl_->device, &img, nullptr, &impl_->colorImage), "vkCreateImage");

    VkMemoryRequirements memReq{};
    vkGetImageMemoryRequirements(impl_->device, impl_->colorImage, &memReq);
    VkMemoryAllocateInfo mai{};
    mai.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize  = memReq.size;
    mai.memoryTypeIndex = findMemoryType(impl_->physicalDevice, memReq.memoryTypeBits,
                                         VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (mai.memoryTypeIndex == UINT32_MAX) {
        return err<bool>(ErrorCode::BackendError, "no device-local memory type");
    }
    VK_TRY(vkAllocateMemory(impl_->device, &mai, nullptr, &impl_->colorMemory),
           "vkAllocateMemory(color)");
    VK_TRY(vkBindImageMemory(impl_->device, impl_->colorImage, impl_->colorMemory, 0),
           "vkBindImageMemory");

    VkImageViewCreateInfo iv{};
    iv.sType                       = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    iv.image                       = impl_->colorImage;
    iv.viewType                    = VK_IMAGE_VIEW_TYPE_2D;
    iv.format                      = impl_->colorFormat;
    iv.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    iv.subresourceRange.levelCount = 1;
    iv.subresourceRange.layerCount = 1;
    VK_TRY(vkCreateImageView(impl_->device, &iv, nullptr, &impl_->colorView),
           "vkCreateImageView");

    // ---- Render pass (clear -> color attachment -> transfer-src for readback) ----
    VkAttachmentDescription color{};
    color.format         = impl_->colorFormat;
    color.samples        = VK_SAMPLE_COUNT_1_BIT;
    color.loadOp         = VK_ATTACHMENT_LOAD_OP_CLEAR;
    color.storeOp        = VK_ATTACHMENT_STORE_OP_STORE;
    color.stencilLoadOp  = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    color.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    color.initialLayout  = VK_IMAGE_LAYOUT_UNDEFINED;
    color.finalLayout    = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;

    VkAttachmentReference colorRef{};
    colorRef.attachment = 0;
    colorRef.layout     = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint    = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments    = &colorRef;

    // Explicit dependencies for the UNDEFINED->COLOR and COLOR->TRANSFER_SRC
    // transitions, so the readback copy is correctly ordered (and validation-clean).
    std::array<VkSubpassDependency, 2> deps{};
    deps[0].srcSubpass    = VK_SUBPASS_EXTERNAL;
    deps[0].dstSubpass    = 0;
    deps[0].srcStageMask  = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
    deps[0].dstStageMask  = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].srcAccessMask = 0;
    deps[0].dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].srcSubpass    = 0;
    deps[1].dstSubpass    = VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask  = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[1].dstStageMask  = VK_PIPELINE_STAGE_TRANSFER_BIT;
    deps[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;

    VkRenderPassCreateInfo rpci{};
    rpci.sType           = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    rpci.attachmentCount = 1;
    rpci.pAttachments    = &color;
    rpci.subpassCount    = 1;
    rpci.pSubpasses      = &subpass;
    rpci.dependencyCount = static_cast<u32>(deps.size());
    rpci.pDependencies   = deps.data();
    VK_TRY(vkCreateRenderPass(impl_->device, &rpci, nullptr, &impl_->renderPass),
           "vkCreateRenderPass");

    VkFramebufferCreateInfo fbci{};
    fbci.sType           = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fbci.renderPass      = impl_->renderPass;
    fbci.attachmentCount = 1;
    fbci.pAttachments    = &impl_->colorView;
    fbci.width           = impl_->extent.width;
    fbci.height          = impl_->extent.height;
    fbci.layers          = 1;
    VK_TRY(vkCreateFramebuffer(impl_->device, &fbci, nullptr, &impl_->framebuffer),
           "vkCreateFramebuffer");

    log::info("[Vulkan] offscreen device ready ({}x{}, validation={})", impl_->extent.width,
              impl_->extent.height, validation_);
    return ok(true);
}

Result<BufferHandle> VulkanDevice::createBuffer(const BufferDesc& desc) {
    if (desc.size == 0) {
        return err<BufferHandle>(ErrorCode::InvalidArgument, "buffer size must be > 0");
    }
    VkBufferCreateInfo bci{};
    bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bci.size  = desc.size;
    bci.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    Impl::BufferRes res;
    if (vkCreateBuffer(impl_->device, &bci, nullptr, &res.buffer) != VK_SUCCESS) {
        return err<BufferHandle>(ErrorCode::BackendError, "vkCreateBuffer failed");
    }
    VkMemoryRequirements req{};
    vkGetBufferMemoryRequirements(impl_->device, res.buffer, &req);
    VkMemoryAllocateInfo mai{};
    mai.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize  = req.size;
    mai.memoryTypeIndex = findMemoryType(impl_->physicalDevice, req.memoryTypeBits,
                                         VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                             VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    if (mai.memoryTypeIndex == UINT32_MAX ||
        vkAllocateMemory(impl_->device, &mai, nullptr, &res.memory) != VK_SUCCESS) {
        vkDestroyBuffer(impl_->device, res.buffer, nullptr);
        return err<BufferHandle>(ErrorCode::OutOfMemory, "buffer memory allocation failed");
    }
    vkBindBufferMemory(impl_->device, res.buffer, res.memory, 0);
    if (desc.initialData != nullptr) {
        void* mapped = nullptr;
        vkMapMemory(impl_->device, res.memory, 0, desc.size, 0, &mapped);
        std::memcpy(mapped, desc.initialData, desc.size);
        vkUnmapMemory(impl_->device, res.memory);
    }
    impl_->buffers.push_back(res);
    return ok(BufferHandle{static_cast<u32>(impl_->buffers.size() - 1)});
}

Result<TextureHandle> VulkanDevice::createTexture(const TextureDesc& desc) {
    // Offscreen path renders to its own target; general textures are not needed yet.
    if (desc.width == 0 || desc.height == 0) {
        return err<TextureHandle>(ErrorCode::InvalidArgument, "texture extent must be > 0");
    }
    return err<TextureHandle>(ErrorCode::Unsupported, "createTexture not implemented yet");
}

Result<ShaderHandle> VulkanDevice::createShader(const ShaderDesc& desc) {
    if (desc.spirv == nullptr || desc.spirvSize == 0 || (desc.spirvSize % 4) != 0) {
        return err<ShaderHandle>(ErrorCode::InvalidArgument, "invalid SPIR-V blob");
    }
    VkShaderModuleCreateInfo smci{};
    smci.sType    = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    smci.codeSize = desc.spirvSize;
    smci.pCode    = reinterpret_cast<const u32*>(desc.spirv);
    VkShaderModule module = VK_NULL_HANDLE;
    if (vkCreateShaderModule(impl_->device, &smci, nullptr, &module) != VK_SUCCESS) {
        return err<ShaderHandle>(ErrorCode::BackendError, "vkCreateShaderModule failed");
    }
    impl_->shaders.push_back(module);
    return ok(ShaderHandle{static_cast<u32>(impl_->shaders.size() - 1)});
}

Result<PipelineHandle> VulkanDevice::createGraphicsPipeline(ShaderHandle vs, ShaderHandle fs) {
    if (vs.id >= impl_->shaders.size() || fs.id >= impl_->shaders.size()) {
        return err<PipelineHandle>(ErrorCode::InvalidArgument, "invalid shader handle");
    }

    std::array<VkPipelineShaderStageCreateInfo, 2> stages{};
    stages[0].sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage  = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = impl_->shaders[vs.id];
    stages[0].pName  = "main";
    stages[1].sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage  = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = impl_->shaders[fs.id];
    stages[1].pName  = "main";

    VkPipelineVertexInputStateCreateInfo vertexInput{}; // no vertex buffers (hardcoded tri)
    vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;

    VkPipelineInputAssemblyStateCreateInfo ia{};
    ia.sType    = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkViewport viewport{0.0f, 0.0f, static_cast<float>(impl_->extent.width),
                        static_cast<float>(impl_->extent.height), 0.0f, 1.0f};
    VkRect2D   scissor{{0, 0}, impl_->extent};
    VkPipelineViewportStateCreateInfo vp{};
    vp.sType         = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    vp.viewportCount = 1;
    vp.pViewports    = &viewport;
    vp.scissorCount  = 1;
    vp.pScissors     = &scissor;

    VkPipelineRasterizationStateCreateInfo rast{};
    rast.sType       = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rast.polygonMode = VK_POLYGON_MODE_FILL;
    rast.cullMode    = VK_CULL_MODE_NONE;
    rast.frontFace   = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    rast.lineWidth   = 1.0f;

    VkPipelineMultisampleStateCreateInfo ms{};
    ms.sType                = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineColorBlendAttachmentState blendAttach{};
    blendAttach.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                 VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    VkPipelineColorBlendStateCreateInfo blend{};
    blend.sType           = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    blend.attachmentCount = 1;
    blend.pAttachments    = &blendAttach;

    Impl::PipelineRes res;
    VkPipelineLayoutCreateInfo plci{};
    plci.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    if (vkCreatePipelineLayout(impl_->device, &plci, nullptr, &res.layout) != VK_SUCCESS) {
        return err<PipelineHandle>(ErrorCode::BackendError, "vkCreatePipelineLayout failed");
    }

    VkGraphicsPipelineCreateInfo gpci{};
    gpci.sType               = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    gpci.stageCount          = static_cast<u32>(stages.size());
    gpci.pStages             = stages.data();
    gpci.pVertexInputState   = &vertexInput;
    gpci.pInputAssemblyState = &ia;
    gpci.pViewportState      = &vp;
    gpci.pRasterizationState = &rast;
    gpci.pMultisampleState   = &ms;
    gpci.pColorBlendState    = &blend;
    gpci.layout              = res.layout;
    gpci.renderPass          = impl_->renderPass;
    gpci.subpass             = 0;
    if (vkCreateGraphicsPipelines(impl_->device, VK_NULL_HANDLE, 1, &gpci, nullptr,
                                  &res.pipeline) != VK_SUCCESS) {
        vkDestroyPipelineLayout(impl_->device, res.layout, nullptr);
        return err<PipelineHandle>(ErrorCode::BackendError, "vkCreateGraphicsPipelines failed");
    }
    impl_->pipelines.push_back(res);
    return ok(PipelineHandle{static_cast<u32>(impl_->pipelines.size() - 1)});
}

void VulkanDevice::destroyBuffer(BufferHandle handle) {
    if (handle.id < impl_->buffers.size()) {
        auto& b = impl_->buffers[handle.id];
        if (b.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(impl_->device, b.buffer, nullptr);
            vkFreeMemory(impl_->device, b.memory, nullptr);
            b = {};
        }
    }
}

void VulkanDevice::destroyTexture(TextureHandle /*handle*/) {}

CommandList* VulkanDevice::beginFrame() {
    vkResetCommandBuffer(impl_->frameCmd, 0);
    VkCommandBufferBeginInfo bi{};
    bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    if (vkBeginCommandBuffer(impl_->frameCmd, &bi) != VK_SUCCESS) {
        return nullptr;
    }
    impl_->commandList.bind(this, impl_->frameCmd);
    return &impl_->commandList;
}

void VulkanDevice::endFrame() {
    if (vkEndCommandBuffer(impl_->frameCmd) != VK_SUCCESS) {
        log::error("[Vulkan] vkEndCommandBuffer failed");
        return;
    }
    VkSubmitInfo submit{};
    submit.sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers    = &impl_->frameCmd;

    vkResetFences(impl_->device, 1, &impl_->frameFence);
    if (vkQueueSubmit(impl_->graphicsQueue, 1, &submit, impl_->frameFence) != VK_SUCCESS) {
        log::error("[Vulkan] vkQueueSubmit failed");
        return;
    }
    vkWaitForFences(impl_->device, 1, &impl_->frameFence, VK_TRUE, UINT64_MAX);
}

void VulkanDevice::present() {
    // Offscreen: nothing to present. Pixels are read back via readColorPixels().
}

void VulkanDevice::waitIdle() {
    if (impl_ && impl_->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(impl_->device);
    }
}

void VulkanDevice::onResize(u32 /*width*/, u32 /*height*/) {
    // Offscreen target is fixed-size; a swapchain path would recreate here.
}

Result<std::vector<u8>> VulkanDevice::readColorPixels(u32& outWidth, u32& outHeight) {
    outWidth  = impl_->extent.width;
    outHeight = impl_->extent.height;
    const VkDeviceSize size = static_cast<VkDeviceSize>(outWidth) * outHeight * 4;

    // Host-visible staging buffer for the readback.
    VkBuffer       staging       = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
    VkBufferCreateInfo bci{};
    bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bci.size  = size;
    bci.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    if (vkCreateBuffer(impl_->device, &bci, nullptr, &staging) != VK_SUCCESS) {
        return err<std::vector<u8>>(ErrorCode::BackendError, "readback buffer create failed");
    }
    VkMemoryRequirements req{};
    vkGetBufferMemoryRequirements(impl_->device, staging, &req);
    VkMemoryAllocateInfo mai{};
    mai.sType           = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize  = req.size;
    mai.memoryTypeIndex = findMemoryType(impl_->physicalDevice, req.memoryTypeBits,
                                         VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                             VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    vkAllocateMemory(impl_->device, &mai, nullptr, &stagingMemory);
    vkBindBufferMemory(impl_->device, staging, stagingMemory, 0);

    // One-shot copy: offscreen image (already TRANSFER_SRC) -> staging buffer.
    VkCommandBufferAllocateInfo cbai{};
    cbai.sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    cbai.commandPool        = impl_->commandPool;
    cbai.level              = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    vkAllocateCommandBuffers(impl_->device, &cbai, &cmd);

    VkCommandBufferBeginInfo bi{};
    bi.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(cmd, &bi);

    VkBufferImageCopy region{};
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.layerCount = 1;
    region.imageExtent                 = {outWidth, outHeight, 1};
    vkCmdCopyImageToBuffer(cmd, impl_->colorImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, staging,
                           1, &region);
    vkEndCommandBuffer(cmd);

    VkSubmitInfo submit{};
    submit.sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers    = &cmd;
    vkResetFences(impl_->device, 1, &impl_->frameFence);
    vkQueueSubmit(impl_->graphicsQueue, 1, &submit, impl_->frameFence);
    vkWaitForFences(impl_->device, 1, &impl_->frameFence, VK_TRUE, UINT64_MAX);
    vkFreeCommandBuffers(impl_->device, impl_->commandPool, 1, &cmd);

    std::vector<u8> pixels(static_cast<usize>(size));
    void*           mapped = nullptr;
    vkMapMemory(impl_->device, stagingMemory, 0, size, 0, &mapped);
    std::memcpy(pixels.data(), mapped, static_cast<usize>(size));
    vkUnmapMemory(impl_->device, stagingMemory);

    vkDestroyBuffer(impl_->device, staging, nullptr);
    vkFreeMemory(impl_->device, stagingMemory, nullptr);
    return ok(std::move(pixels));
}

u32 VulkanDevice::renderWidth() const noexcept { return impl_ ? impl_->extent.width : 0; }
u32 VulkanDevice::renderHeight() const noexcept { return impl_ ? impl_->extent.height : 0; }

// ---------------------------------------------------------------------------
// VulkanCommandList — records into the bound frame command buffer.
// ---------------------------------------------------------------------------
void VulkanCommandList::beginRenderPass(RenderPassHandle /*pass*/, ClearColor clear) {
    auto* cmd = static_cast<VkCommandBuffer>(native_);
    auto* d   = device_->impl_.get();

    VkClearValue clearValue{};
    clearValue.color = {{clear.r, clear.g, clear.b, clear.a}};

    VkRenderPassBeginInfo rpbi{};
    rpbi.sType             = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpbi.renderPass        = d->renderPass;
    rpbi.framebuffer       = d->framebuffer;
    rpbi.renderArea.extent = d->extent;
    rpbi.clearValueCount   = 1;
    rpbi.pClearValues      = &clearValue;
    vkCmdBeginRenderPass(cmd, &rpbi, VK_SUBPASS_CONTENTS_INLINE);
}

void VulkanCommandList::endRenderPass() {
    vkCmdEndRenderPass(static_cast<VkCommandBuffer>(native_));
}

void VulkanCommandList::bindPipeline(PipelineHandle pipeline) {
    auto* d = device_->impl_.get();
    if (pipeline.id < d->pipelines.size()) {
        vkCmdBindPipeline(static_cast<VkCommandBuffer>(native_),
                          VK_PIPELINE_BIND_POINT_GRAPHICS, d->pipelines[pipeline.id].pipeline);
    }
}

void VulkanCommandList::bindVertexBuffer(BufferHandle buffer) {
    auto* d = device_->impl_.get();
    if (buffer.id < d->buffers.size() && d->buffers[buffer.id].buffer != VK_NULL_HANDLE) {
        VkDeviceSize offset = 0;
        vkCmdBindVertexBuffers(static_cast<VkCommandBuffer>(native_), 0, 1,
                               &d->buffers[buffer.id].buffer, &offset);
    }
}

void VulkanCommandList::setViewport(const Viewport& /*vp*/) {
    // Pipeline uses a static viewport sized to the offscreen target; nothing to do.
}

void VulkanCommandList::draw(u32 vertexCount, u32 instanceCount) {
    vkCmdDraw(static_cast<VkCommandBuffer>(native_), vertexCount, instanceCount, 0, 0);
}

} // namespace engine::rhi::vulkan

#endif // ENGINE_HAS_VULKAN
