/*!
 * \author ddubois 
 * \date 03-Dec-17.
 */

#include <easylogging++.h>
#include <nova/profiler.h>
#include "shader_resource_manager.h"
#include "../../vulkan/render_context.h"
#include "../../nova_renderer.h"

namespace nova {
    shader_resource_manager::shader_resource_manager(std::shared_ptr<render_context> context) : device(context->device), textures(context), buffers(context), context(context) {
        create_point_sampler();
    }

    void shader_resource_manager::create_descriptor_pool(uint32_t num_sets, uint32_t num_buffers, uint32_t num_textures) {
        vk::DescriptorPoolCreateInfo pool_create_info = {};
        pool_create_info.maxSets = num_sets;
        pool_create_info.poolSizeCount = 2;

        vk::DescriptorPoolSize sizes[] = {
                vk::DescriptorPoolSize().setType(vk::DescriptorType::eCombinedImageSampler).setDescriptorCount(num_textures),
                vk::DescriptorPoolSize().setType(vk::DescriptorType::eUniformBuffer).setDescriptorCount(num_buffers),
        };

        pool_create_info.pPoolSizes = sizes;
        pool_create_info.flags = vk::DescriptorPoolCreateFlagBits::eFreeDescriptorSet;

        descriptor_pool = device.createDescriptorPool(pool_create_info);

        auto model_matrix_descriptor_binding = vk::DescriptorSetLayoutBinding()
            .setDescriptorType(vk::DescriptorType::eUniformBuffer)
            .setDescriptorCount(1)
            .setStageFlags(vk::ShaderStageFlagBits::eVertex);

        auto model_matrix_descriptor_create_info = vk::DescriptorSetLayoutCreateInfo()
            .setBindingCount(1)
            .setPBindings(&model_matrix_descriptor_binding);

        model_matrix_layout = device.createDescriptorSetLayout({model_matrix_descriptor_create_info});

        model_matrix_descriptor_allocate_info = vk::DescriptorSetAllocateInfo()
                    .setDescriptorPool(descriptor_pool)
                    .setDescriptorSetCount(1)
                    .setPSetLayouts(&model_matrix_layout);
    }

    shader_resource_manager::~shader_resource_manager() {
        device.destroyDescriptorPool(descriptor_pool);

        device.destroySampler(point_sampler);
    }

    std::vector<vk::DescriptorSet> shader_resource_manager::create_descriptor_sets_for_pipeline(const pipeline_object &pipeline_data) {
        if(pipeline_data.layouts.empty()) {
            // If there's no layouts, we're done
            return {};
        }

        LOG(INFO) << "Creating descriptor sets for pipeline " << pipeline_data.name;

        auto layouts = std::vector<vk::DescriptorSetLayout>{};
        layouts.reserve(pipeline_data.layouts.size());

        // CLion might tell you to simplify this into a foreach loop... DO NOT! The layouts need to be added in set
        // order, not map order which is what you'll get if you use a foreach - AND IT'S WRONG
        for(uint32_t i = 0; i < pipeline_data.layouts.size(); i++) {
            layouts.push_back(pipeline_data.layouts.at(static_cast<int32_t>(i)));
        }

        auto alloc_info = vk::DescriptorSetAllocateInfo()
            .setDescriptorPool(descriptor_pool)
            .setDescriptorSetCount(static_cast<uint32_t>(layouts.size()))
            .setPSetLayouts(layouts.data());

        std::vector<vk::DescriptorSet> descriptors = device.allocateDescriptorSets(alloc_info);

        total_allocated_descriptor_sets += layouts.size();
        LOG(DEBUG) << "We've created " << total_allocated_descriptor_sets << " sets";

        return descriptors;
    }

    void shader_resource_manager::create_point_sampler() {
        auto sampler_create = vk::SamplerCreateInfo()
            .setMagFilter(vk::Filter::eNearest)
            .setMinFilter(vk::Filter::eNearest)
            .setMipmapMode(vk::SamplerMipmapMode::eLinear)
            .setAddressModeU(vk::SamplerAddressMode::eClampToEdge)
            .setAddressModeV(vk::SamplerAddressMode::eClampToEdge)
            .setAddressModeW(vk::SamplerAddressMode::eClampToEdge)
            .setMipLodBias(0)
            .setAnisotropyEnable(false)
            .setCompareEnable(false)
            .setMinLod(0)
            .setMaxLod(32)
            .setUnnormalizedCoordinates(false)
            .setBorderColor(vk::BorderColor::eFloatTransparentBlack);

        point_sampler = device.createSampler(sampler_create);
        LOG(INFO) << "Created point sampler " << (VkSampler)point_sampler;
    }

    vk::Sampler shader_resource_manager::get_point_sampler() {
        return point_sampler;
    }

    texture_manager &shader_resource_manager::get_texture_manager() {
        return textures;
    }

    uniform_buffer_store &shader_resource_manager::get_uniform_buffers() {
        return buffers;
    }

    void shader_resource_manager::create_descriptor_sets(const std::unordered_map<std::string, std::vector<pipeline_object>> &pipelines, std::unordered_map<std::string, std::vector<material_pass>>& material_passes) {
        NOVA_PROFILER_SCOPE;
        uint32_t num_sets = 0, num_textures = 0, num_buffers = 0;

        for(const auto &named_pipeline : pipelines) {
            for(const auto &pipeline : named_pipeline.second) {

                num_sets += pipeline.layouts.size();

                for(const auto &named_binding : pipeline.resource_bindings) {
                    const resource_binding &binding = named_binding.second;

                    if(binding.descriptorType == vk::DescriptorType::eUniformBuffer) {
                        num_buffers++;

                    } else if(binding.descriptorType == vk::DescriptorType::eCombinedImageSampler) {
                        num_textures += material_passes.size();

                    } else {
                        LOG(WARNING) << "Descriptor type " << vk::to_string(binding.descriptorType)
                                     << " is not supported yet";
                    }
                }
            }
        }

        create_descriptor_pool(10000 + num_sets, 10000 + num_buffers, num_textures);

        for(const auto &named_pipeline : pipelines) {
            for(const auto& pipeline : named_pipeline.second) {
                auto& mats = material_passes[pipeline.name];
                for(material_pass& mat : mats) {
                    mat.descriptor_sets = create_descriptor_sets_for_pipeline(pipeline);
                    update_all_descriptor_sets(mat, pipeline.resource_bindings);
                }
            }
        }
    }

    vk::DescriptorSet shader_resource_manager::create_model_matrix_descriptor() {
        LOG(DEBUG) << "Creating per-model descriptor " << ++per_model_descriptor_count;
        LOG(DEBUG) << "We've allocated " << ++total_allocated_descriptor_sets << " in total";
        auto descriptor = device.allocateDescriptorSets(model_matrix_descriptor_allocate_info)[0];
        LOG(INFO) << "Allocated descriptor " << (VkDescriptorSet)descriptor;
        return descriptor;
    }

    void shader_resource_manager::free_descriptor(vk::DescriptorSet to_free) {
        LOG(INFO) << "Freeing descriptor " << (VkDescriptorSet)to_free;
        per_model_descriptor_count--;
        total_allocated_descriptor_sets--;
        device.freeDescriptorSets(descriptor_pool, {to_free});
    }

    void shader_resource_manager::update_all_descriptor_sets(const material_pass &mat, const std::unordered_map<std::string, resource_binding> &name_to_descriptor) {
        // for each resource:
        //  - Get its set and binding from the pipeline
        //  - Update its descriptor set
        LOG(DEBUG) << "Updating descriptors for material " << mat.material_name;

        std::vector<vk::WriteDescriptorSet> writes;
        std::vector<vk::DescriptorImageInfo> image_infos(mat.bindings.size());
        std::vector<vk::DescriptorBufferInfo> buffer_infos(mat.bindings.size());

        for(const auto& binding : mat.bindings) {
            const auto& descriptor_info = name_to_descriptor.at(binding.first);
            const auto& resource_name = binding.second;
            const auto descriptor_set = mat.descriptor_sets[descriptor_info.set];
            bool is_known = false;

            auto write = vk::WriteDescriptorSet()
                    .setDstSet(descriptor_set)
                    .setDstBinding(descriptor_info.binding)
                    .setDescriptorCount(1)
                    .setDstArrayElement(0);

            if(textures.is_texture_known(resource_name)) {
                is_known = true;

                auto& texture = textures.get_texture(resource_name);

                auto image_info = vk::DescriptorImageInfo()
                        .setImageView(texture.get_image_view())
                        .setImageLayout(vk::ImageLayout::eShaderReadOnlyOptimal)
                        .setSampler(get_point_sampler());

                image_infos.push_back(image_info);

                write.setPImageInfo(&image_infos[image_infos.size() - 1])
                     .setDescriptorType(vk::DescriptorType::eCombinedImageSampler);

                LOG(INFO) << "Binding texture " << texture.get_name() << " to descriptor (set=" << descriptor_info.set << " binding=" << descriptor_info.binding << ")";

            } else if(buffers.is_buffer_known(resource_name)) {
                is_known = true;

                auto& buffer = buffers.get_buffer(resource_name);

                auto buffer_info = vk::DescriptorBufferInfo()
                    .setBuffer(buffer.get_vk_buffer())
                    .setOffset(0)
                    .setRange(buffer.get_size());

                buffer_infos.push_back(buffer_info);

                write.setPBufferInfo(&buffer_infos[buffer_infos.size() - 1])
                        .setDescriptorType(vk::DescriptorType::eUniformBuffer);

                LOG(INFO) << "Binding buffer " << resource_name << " to descriptor " << "(id=" << (VkDescriptorSet)descriptor_set << " set=" << descriptor_info.set << " binding=" << descriptor_info.binding << ")";

            } else {
                LOG(WARNING) << "Resource " << resource_name << " is not known to Nova. I hope you aren't using it cause it doesn't exist";
            }

            if(is_known) {
                writes.push_back(write);
            }
        }

        device.updateDescriptorSets(writes, {});
    }
}
