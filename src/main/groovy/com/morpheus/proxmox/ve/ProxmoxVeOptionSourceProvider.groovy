package com.morpheus.proxmox.ve

import com.morpheusdata.core.AbstractOptionSourceProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.model.projection.ComputeServerIdentityProjection
import groovy.util.logging.Slf4j

@Slf4j
class ProxmoxVeOptionSourceProvider extends AbstractOptionSourceProvider {

    ProxmoxVePlugin plugin
    MorpheusContext morpheusContext

    ProxmoxVeOptionSourceProvider(ProxmoxVePlugin plugin, MorpheusContext context) {
        this.plugin = plugin
        this.morpheusContext = context
    }

    @Override
    MorpheusContext getMorpheus() {
        return this.morpheusContext
    }

    @Override
    Plugin getPlugin() {
        return this.plugin
    }

    @Override
    String getCode() {
        return 'proxmox-ve-option-source'
    }

    @Override
    String getName() {
        return 'Proxmox VE Option Source'
    }

    @Override
    List<String> getMethodNames() {
        return new ArrayList<String>(['proxmoxVeProvisionImage', 'proxmoxVeNode'])
    }


    def proxmoxVeProvisionImage(args) {
        log.debug "proxmoxVeProvisionImage: ${args}"
        def cloudId = args?.size() > 0 ? args.getAt(0).zoneId.toLong() : null

        def options = []
        def invalidStatus = ['Saving', 'Failed', 'Converting']
        def virtualImages = morpheusContext.async.virtualImage.list(
                new DataQuery().
                        withFilter('refId', cloudId).
                        withFilter('category', 'proxmox.image')
        ).blockingSubscribe() {
            if (it.deleted == false &&
                !(it.status in invalidStatus)) {
                options << [name: it.name, value: it.id]
            }
        }

        if (options.size() > 0) {
            options = options.sort { it.name }
        }

        log.error("FOUND ${options.size()} VirtualImages...")
        return options
    }


    def proxmoxVeNode(args) {
        log.debug "proxmoxVeNode: ${args}"
        def cloudId = args?.size() > 0 ? args.getAt(0).zoneId.toLong() : null

        def options = []

        def domainRecords = morpheusContext.async.computeServer.listIdentityProjections(cloudId, null).filter {
            ComputeServerIdentityProjection projection ->
                if (projection.category == "proxmox.ve.host.$cloudId") {
                    return true
                }
                false
        }.blockingSubscribe() {
            options << [name: it.name, value: it.externalId]
        }

        if (options.size() > 0) {
            options = options.sort { it.name }
        }

        log.error("FOUND ${options.size()} ComputeServer Nodes...")
        return options
    }
}