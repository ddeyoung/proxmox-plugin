/*
* Copyright 2022 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.morpheus.proxmox.ve

import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.AccountCredential
import groovy.util.logging.Slf4j


@Slf4j
class ProxmoxVePlugin extends Plugin {

    @Override
    String getCode() {
        return 'proxmox-ve'
    }

    @Override
    void initialize() {
        this.setName("Proxmox VE")
        this.registerProvider(new ProxmoxVeCloudProvider(this,this.morpheus))
        this.registerProvider(new ProxmoxVeProvisionProvider(this,this.morpheus))
        this.registerProvider(new ProxmoxVeOptionSourceProvider(this,this.morpheus))
    }


    def getAuthConfig(Cloud cloud) {
        log.debug "getAuthConfig: ${cloud}"
        def rtn = [
                apiUrl    : cloud.serviceUrl,
                v2basePath: '/api2/json',
                username  : null,
                password  : null
        ]

        if(!cloud.accountCredentialLoaded) {
            AccountCredential accountCredential
            try {
                accountCredential = this.morpheus.async.cloud.loadCredentials(cloud.id).blockingGet()
            } catch(e) {
               log.error("Error loading cloud credentials: ${e}")
            }
            cloud.accountCredentialLoaded = true
            cloud.accountCredentialData = accountCredential?.data
        }

        if(cloud.accountCredentialData && cloud.accountCredentialData.containsKey('username')) {
            rtn.username = cloud.accountCredentialData['username']
        } else {
            rtn.username = cloud.serviceUsername
        }

        if(cloud.accountCredentialData && cloud.accountCredentialData.containsKey('password')) {
            rtn.password = cloud.accountCredentialData['password']
        } else {
            rtn.password = cloud.servicePassword
        }
        return rtn
    }


    /**
     * Called when a plugin is being removed from the plugin manager (aka Uninstalled)
     */
    @Override
    void onDestroy() {
        //nothing to do for now
    }
}
