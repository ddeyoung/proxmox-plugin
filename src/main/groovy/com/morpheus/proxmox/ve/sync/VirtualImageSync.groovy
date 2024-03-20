package com.morpheus.proxmox.ve.sync

import com.morpheus.proxmox.ve.ProxmoxVePlugin
import com.morpheus.proxmox.ve.util.ProxmoxComputeUtil
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.providers.CloudProvider
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.core.util.SyncTask
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.CloudPool
import com.morpheusdata.model.Network
import com.morpheusdata.model.OsType
import com.morpheusdata.model.VirtualImage
import com.morpheusdata.model.projection.NetworkIdentityProjection
import com.morpheusdata.model.projection.VirtualImageIdentityProjection
import groovy.util.logging.Slf4j
import io.reactivex.rxjava3.core.Observable

import static com.morpheusdata.model.ImageType.qcow2


@Slf4j
class VirtualImageSync {

    private Cloud cloud
    private MorpheusContext context
    private ProxmoxVePlugin plugin
    private HttpApiClient apiClient
    private CloudProvider cloudProvider
    private Map authConfig


    VirtualImageSync(ProxmoxVePlugin proxmoxVePlugin, Cloud cloud, HttpApiClient apiClient, CloudProvider cloudProvider) {
        this.@plugin = proxmoxVePlugin
        this.@cloud = cloud
        this.@apiClient = apiClient
        this.@context = proxmoxVePlugin.morpheus
        this.@cloudProvider = cloudProvider
        this.@authConfig = plugin.getAuthConfig(cloud)
    }



    def execute() {
        try {
            log.debug "Execute VirtualImageSync STARTED: ${cloud.id}"
            def cloudItems = ProxmoxComputeUtil.listTemplates(apiClient, authConfig).data
            log.debug("Proxmox templates found: $cloudItems")

            Observable domainRecords = context.async.virtualImage.listIdentityProjections(new DataQuery().withFilter(
                    new DataFilter("refType", "ComputeZone")).withFilter(
                    new DataFilter("refId", cloud.id)))

            SyncTask<VirtualImageIdentityProjection, Map, VirtualImage> syncTask = new SyncTask<>(domainRecords, cloudItems)
            syncTask.addMatchFunction { VirtualImageIdentityProjection domainObject, Map cloudItem ->
                domainObject.externalId == cloudItem.vmid.toString()
            }.onAdd { List<Map> newItems ->
                addMissingVirtualImages(newItems)
            }.withLoadObjectDetails { List<SyncTask.UpdateItemDto<VirtualImageIdentityProjection, Map>> updateItems ->
                Map<Long, SyncTask.UpdateItemDto<VirtualImageIdentityProjection, Map>> updateItemMap = updateItems.collectEntries { [(it.existingItem.id): it]}
                return context.async.virtualImage.listById(updateItems?.collect { it.existingItem.id }).map { VirtualImage vi ->
                    return new SyncTask.UpdateItem<VirtualImage, Map>(existingItem: vi, masterItem: updateItemMap[vi.id].masterItem)
                } 
            }.onUpdate { List<SyncTask.UpdateItem<VirtualImage, Map>> updateItems ->
                updateMatchedVirtualImages(updateItems)
            }.onDelete { removeItems ->
                removeMissingVirtualImages(removeItems)
            }.start()
        } catch(e) {
            log.error "Error in VirtualImageSync execute : ${e}", e
        }
        log.debug "Execute VirtualImageSync COMPLETED: ${cloud.id}"
    }


    private addMissingVirtualImages(Collection<Map> addList) {
        log.debug "addMissingVirtualImages ${addList?.size()}"

        def adds = []
        addList.each {
            log.debug("Creating virtual image: $it")
            adds << new VirtualImage(
                installAgent: false,
                externalId: it.vmid,
                imageType: qcow2,
                name: it.name,
                ownerId: cloud.account.id,
                systemImage: false,
                deleted: false,
                visibility: "visible",
                owner: cloud.account,
                account: cloud.account,
                code: "proxmox.image.${it.vmid}",
                architecture: "qemu",
                isPublic: true,
                minDisk: it.maxdisk,
                minRam: it.maxmem,
                status: 'Active',
                osType: new OsType(name: "Proxmox Image", platform: "linux"),
                category: 'proxmox.image',
                platform: "linux",
                refType: 'ComputeZone',
                refId: cloud.id,
            )
        }

        log.debug "About to create ${adds.size()} virtualImages"
        context.async.virtualImage.create(adds, cloud).blockingGet()
    }


    private updateMatchedVirtualImages(List<SyncTask.UpdateItem<VirtualImage, Map>> updateItems) {
        for (def updateItem in updateItems) {
            def existingItem = updateItem.existingItem
            def cloudItem = updateItem.masterItem

            //Add update logic here...
            //updateMachineMetrics()
        }

        //Example:
        // Nutanix - https://github.com/gomorpheus/morpheus-nutanix-prism-plugin/blob/master/src/main/groovy/com/morpheusdata/nutanix/prism/plugin/sync/ImagesSync.groovy
        // Openstack - https://github.com/gomorpheus/morpheus-openstack-plugin/blob/main/src/main/groovy/com/morpheusdata/openstack/plugin/sync/ImagesSync.groovy
    }


    private removeMissingVirtualImages(List<VirtualImageIdentityProjection> removeItems) {
        log.info("Remove virtual images...")
        context.async.virtualImage.bulkRemove(removeItems).blockingGet()
    }
}