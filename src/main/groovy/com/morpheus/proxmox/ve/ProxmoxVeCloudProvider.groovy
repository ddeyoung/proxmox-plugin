package com.morpheus.proxmox.ve

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.core.providers.CloudProvider
import com.morpheusdata.core.providers.ProvisionProvider
import com.morpheusdata.core.util.HttpApiClient
import com.morpheusdata.model.BackupProvider
import com.morpheusdata.model.Cloud
import com.morpheusdata.model.CloudFolder
import com.morpheusdata.model.CloudPool
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.ComputeServerType
import com.morpheusdata.model.Datastore
import com.morpheusdata.model.Icon
import com.morpheusdata.model.Network
import com.morpheusdata.model.NetworkSubnetType
import com.morpheusdata.model.NetworkType
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.StorageControllerType
import com.morpheusdata.model.StorageVolumeType
import com.morpheusdata.request.ValidateCloudRequest
import com.morpheusdata.response.ServiceResponse
import com.morpheus.proxmox.ve.util.ProxmoxComputeUtil
import com.morpheus.proxmox.ve.sync.HostSync
import com.morpheus.proxmox.ve.sync.DatastoreSync
import com.morpheus.proxmox.ve.sync.NetworkSync
import com.morpheus.proxmox.ve.sync.VirtualImageSync
import com.morpheus.proxmox.ve.sync.VMSync
import groovy.util.logging.Slf4j


@Slf4j
class ProxmoxVeCloudProvider implements CloudProvider {
	public static final String CLOUD_PROVIDER_CODE = 'proxmox-ve.cloud'

	protected MorpheusContext context
	protected Plugin plugin

	public ProxmoxVeCloudProvider(Plugin plugin, MorpheusContext ctx) {
		super()
		this.@plugin = plugin
		this.@context = ctx
	}

	/**
	 * Grabs the description for the CloudProvider
	 * @return String
	 */
	@Override
	String getDescription() {
		return 'Proxmox Virtual Environment Integration'
	}

	/**
	 * Returns the Cloud logo for display when a user needs to view or add this cloud. SVGs are preferred.
	 * @since 0.13.0
	 * @return Icon representation of assets stored in the src/assets of the project.
	 */
	@Override
	Icon getIcon() {
		return new Icon(path:'proxmox-full-lockup-color.svg', darkPath:'proxmox-full-lockup-inverted-color.svg')
	}

	/**
	 * Returns the circular Cloud logo for display when a user needs to view or add this cloud. SVGs are preferred.
	 * @since 0.13.6
	 * @return Icon
	 */
	@Override
	Icon getCircularIcon() {
		return new Icon(path:'proxmox-logo-stacked-color.svg', darkPath:'proxmox-logo-stacked-inverted-color.svg')
	}

	/**
	 * Provides a Collection of OptionType inputs that define the required input fields for defining a cloud integration
	 * @return Collection of OptionType
	 */
	@Override
	Collection<OptionType> getOptionTypes() {
		Collection<OptionType> options = []

		options << new OptionType(
				name: 'Proxmox API URL',
				code: 'proxmox-url',
				displayOrder: 0,
				fieldContext: 'domain',
				fieldLabel: 'Proxmox API URL',
				fieldCode: 'gomorpheus.optiontype.serviceUrl',
				fieldName: 'serviceUrl',
				inputType: OptionType.InputType.TEXT,
				required: true,
				defaultValue: ""
		)
		options << new OptionType(
				code: 'proxmox-credential',
				inputType: OptionType.InputType.CREDENTIAL,
				name: 'Credentials',
				fieldName: 'type',
				fieldLabel: 'Credentials',
				fieldContext: 'credential',
				required: true,
				defaultValue: 'local',
				displayOrder: 1,
				optionSource: 'credentials',
				config: '{"credentialTypes":["username-password"]}'
		)
		options << new OptionType(
				name: 'User Name',
				code: 'proxmox-username',
				displayOrder: 2,
				fieldContext: 'config',
				fieldLabel: 'User Name',
				fieldCode: 'gomorpheus.optiontype.UserName',
				fieldName: 'username',
				inputType: OptionType.InputType.TEXT,
				localCredential: true,
				required: true
		)
		options << new OptionType(
				name: 'Password',
				code: 'proxmox-password',
				displayOrder: 3,
				fieldContext: 'config',
				fieldLabel: 'Password',
				fieldCode: 'gomorpheus.optiontype.Password',
				fieldName: 'password',
				inputType: OptionType.InputType.PASSWORD,
				localCredential: true,
				required: true
		)
/*		options << new OptionType(
				name: 'Proxmox Token',
				code: 'proxmox-token',
				displayOrder: 4,
				fieldContext: 'config',
				fieldLabel: 'Proxmox Token',
				fieldCode: 'gomorpheus.optiontype.Token',
				fieldName: 'token',
				inputType: OptionType.InputType.PASSWORD,
				localCredential: false,
				required: true
		)
*/
		return options
	}
	/**
	 * Grabs available provisioning providers related to the target Cloud Plugin. Some clouds have multiple provisioning
	 * providers or some clouds allow for service based providers on top like (Docker or Kubernetes).
	 * @return Collection of ProvisionProvider
	 */
	@Override
	Collection<ProvisionProvider> getAvailableProvisionProviders() {
	    return this.@plugin.getProvidersByType(ProvisionProvider) as Collection<ProvisionProvider>
	}

	/**
	 * Grabs available backup providers related to the target Cloud Plugin.
	 * @return Collection of BackupProvider
	 */
	@Override
	Collection<BackupProvider> getAvailableBackupProviders() {
		Collection<BackupProvider> providers = []
		return providers
	}

	/**
	 * Provides a Collection of {@link NetworkType} related to this CloudProvider
	 * @return Collection of NetworkType
	 */
	@Override
	Collection<NetworkType> getNetworkTypes() {

		NetworkType bridgeNetwork = new NetworkType([
				code              : 'proxmox-ve-bridge-network',
				externalType      : 'LinuxBridge',
				cidrEditable      : false,
				dhcpServerEditable: false,
				dnsEditable       : true,
				gatewayEditable   : false,
				vlanIdEditable    : false,
				canAssignPool     : true,
				name              : 'Proxmox VE Bridge Network',
				hasNetworkServer  : true,
				creatable: true
		])

		return [bridgeNetwork]
	}

	/**
	 * Provides a Collection of {@link NetworkSubnetType} related to this CloudProvider
	 * @return Collection of NetworkSubnetType
	 */
	@Override
	Collection<NetworkSubnetType> getSubnetTypes() {
		Collection<NetworkSubnetType> subnets = []
		return subnets
	}

	/**
	 * Provides a Collection of {@link StorageVolumeType} related to this CloudProvider
	 * @return Collection of StorageVolumeType
	 */
	@Override
	Collection<StorageVolumeType> getStorageVolumeTypes() {
		Collection<StorageVolumeType> volumeTypes = []

		volumeTypes << new StorageVolumeType(
				name: "Proxmox VM Generic Volume Type",
				code: "proxmox.vm.generic.volume.type",
				displayOrder: 0
		)

		return volumeTypes
	}

	/**
	 * Provides a Collection of {@link StorageControllerType} related to this CloudProvider
	 * @return Collection of StorageControllerType
	 */
	@Override
	Collection<StorageControllerType> getStorageControllerTypes() {
		Collection<StorageControllerType> controllerTypes = []
		return controllerTypes
	}

	/**
	 * Grabs all {@link ComputeServerType} objects that this CloudProvider can represent during a sync or during a provision.
	 * @return collection of ComputeServerType
	 */
	@Override
	Collection<ComputeServerType> getComputeServerTypes() {
		Collection<ComputeServerType> serverTypes = []

		serverTypes << new ComputeServerType (
				name: 'Proxmox VE Node',
				code: 'proxmox-ve-node',
				description: 'Proxmox VE Node',
				vmHypervisor: true,
				controlPower: false,
				reconfigureSupported: false,
				externalDelete: false,
				hasAutomation: false,
				agentType: ComputeServerType.AgentType.none,
				platform: PlatformType.unknown,
				managed: false,
				provisionTypeCode: 'proxmox-ve-provider',
				nodeType: 'proxmox-ve-node'
		)
		serverTypes << new ComputeServerType (
				name: 'Proxmox VE VM',
				code: 'proxmox-qemu-vm',
				description: 'Proxmox VE Qemu VM',
				vmHypervisor: false,
				controlPower: true,
				reconfigureSupported: false,
				externalDelete: false,
				hasAutomation: true,
				agentType: ComputeServerType.AgentType.none,
				platform: PlatformType.unknown,
				managed: false,
				provisionTypeCode: 'proxmox-ve-provider',
				nodeType: 'proxmox-qemu-vm'
		)
		return serverTypes
	}


	/**
	 * Validates the submitted cloud information to make sure it is functioning correctly.
	 * If a {@link ServiceResponse} is not marked as successful then the validation results will be
	 * bubbled up to the user.
	 * @param cloudInfo cloud
	 * @param validateCloudRequest Additional validation information
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse validate(Cloud cloudInfo, ValidateCloudRequest validateCloudRequest) {	
		log.info("validate: {}", cloudInfo)
		try {
			if(!cloudInfo) {
				return new ServiceResponse(success: false, msg: 'No cloud found')
			}

			def username, password
			def baseUrl = cloudInfo.serviceUrl
			log.debug("Service URL: $baseUrl")

			// Provided creds vs. Infra > Trust creds
			if (validateCloudRequest.credentialType == 'username-password') {
				username = validateCloudRequest.credentialUsername ?: cloudInfo.serviceUsername
				password = validateCloudRequest.credentialPassword ?: cloudInfo.servicePassword
			} else if (validateCloudRequest.credentialType == 'local') {
				username = cloudInfo.getConfigMap().get("username")
				password = cloudInfo.getConfigMap().get("password")
			} else {
				return new ServiceResponse(success: false, msg: "Unknown credential source type $validateCloudRequest.credentialType")
			}

			// Integration needs creds and a base URL
			if (username?.length() < 1 ) {
				return new ServiceResponse(success: false, msg: 'Enter a username.')
			} else if (password?.length() < 1) {
				return new ServiceResponse(success: false, msg: 'Enter a password.')
			} else if (cloudInfo.serviceUrl.length() < 1) {
				return new ServiceResponse(success: false, msg: 'Enter a base url.')
			}

			// Setup token get using util class
			log.info("Attempting authentication to populate access token and csrf token.")
			def tokenTest = ProxmoxComputeUtil.getApiV2Token(username, password, baseUrl)
			if (tokenTest.success) {
				return new ServiceResponse(success: true, msg: 'Cloud connection validated using provided credentials and URL...')
			} else {
				return new ServiceResponse(success: false, msg: 'Unable to validate cloud connection using provided credentials and URL')
			}
		} catch(e) {
			log.error('Error validating cloud', e)
			return new ServiceResponse(success: false, msg: "Error validating cloud ${e}")
		}
	}

	/**
	 * Called when a Cloud From Morpheus is first saved. This is a hook provided to take care of initial state
	 * assignment that may need to take place.
	 * @param cloudInfo instance of the cloud object that is being initialized.
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse initializeCloud(Cloud cloudInfo) {
		
		plugin.getNetworkProvider().initializeProvider(cloudInfo)


		return ServiceResponse.success()
	}

	/**
	 * Zones/Clouds are refreshed periodically by the Morpheus Environment. This includes things like caching of brownfield
	 * environments and resources such as Networks, Datastores, Resource Pools, etc.
	 * @param cloudInfo cloud
	 * @return ServiceResponse. If ServiceResponse.success == true, then Cloud status will be set to Cloud.Status.ok. If
	 * ServiceResponse.success == false, the Cloud status will be set to ServiceResponse.data['status'] or Cloud.Status.error
	 * if not specified. So, to indicate that the Cloud is offline, return `ServiceResponse.error('cloud is not reachable', null, [status: Cloud.Status.offline])`
	 */
	@Override
	ServiceResponse refresh(Cloud cloudInfo) {

		log.debug("Refresh triggered, service url is: " + cloudInfo.serviceUrl)
		HttpApiClient client = new HttpApiClient()
		try {

			(new HostSync(plugin, cloudInfo, client)).execute()
			(new DatastoreSync(plugin, cloudInfo, client)).execute()
			(new NetworkSync(plugin, cloudInfo, client)).execute()
			(new VMSync(plugin, cloudInfo, client, this)).execute()
			(new VirtualImageSync(plugin, cloudInfo, client, this)).execute()

		} catch (e) {
			log.error("refresh cloud error: ${e}", e)
		} finally {
			if(client) {
				client.shutdownClient()
			}
		}
		return ServiceResponse.success()
	}

	/**
	 * Zones/Clouds are refreshed periodically by the Morpheus Environment. This includes things like caching of brownfield
	 * environments and resources such as Networks, Datastores, Resource Pools, etc. This represents the long term sync method that happens
	 * daily instead of every 5-10 minute cycle
	 * @param cloudInfo cloud
	 */
	@Override
	void refreshDaily(Cloud cloudInfo) {
	}

	/**
	 * Called when a Cloud From Morpheus is removed. This is a hook provided to take care of cleaning up any state.
	 * @param cloudInfo instance of the cloud object that is being removed.
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse deleteCloud(Cloud cloudInfo) {
		return ServiceResponse.success()
	}

	/**
	 * Returns whether the cloud supports {@link CloudPool}
	 * @return Boolean
	 */
	@Override
	Boolean hasComputeZonePools() {
		return false
	}

	/**
	 * Returns whether a cloud supports {@link Network}
	 * @return Boolean
	 */
	@Override
	Boolean hasNetworks() {
		return true
	}

	/**
	 * Returns whether a cloud supports {@link CloudFolder}
	 * @return Boolean
	 */
	@Override
	Boolean hasFolders() {
		return false
	}

	/**
	 * Returns whether a cloud supports {@link Datastore}
	 * @return Boolean
	 */
	@Override
	Boolean hasDatastores() {
		return true
	}

	/**
	 * Returns whether a cloud supports bare metal VMs
	 * @return Boolean
	 */
	@Override
	Boolean hasBareMetal() {
		return false
	}

	/**
	 * Indicates if the cloud supports cloud-init. Returning true will allow configuration of the Cloud
	 * to allow installing the agent remotely via SSH /WinRM or via Cloud Init
	 * @return Boolean
	 */
	@Override
	Boolean hasCloudInit() {
		return true
	}

	/**
	 * Indicates if the cloud supports the distributed worker functionality
	 * @return Boolean
	 */
	@Override
	Boolean supportsDistributedWorker() {
		return false
	}

	/**
	 * Called when a server should be started. Returning a response of success will cause corresponding updates to usage
	 * records, result in the powerState of the computeServer to be set to 'on', and related instances set to 'running'
	 * @param computeServer server to start
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse startServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * Called when a server should be stopped. Returning a response of success will cause corresponding updates to usage
	 * records, result in the powerState of the computeServer to be set to 'off', and related instances set to 'stopped'
	 * @param computeServer server to stop
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse stopServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * Called when a server should be deleted from the Cloud.
	 * @param computeServer server to delete
	 * @return ServiceResponse
	 */
	@Override
	ServiceResponse deleteServer(ComputeServer computeServer) {
		return ServiceResponse.success()
	}

	/**
	 * Grabs the singleton instance of the provisioning provider based on the code defined in its implementation.
	 * Typically Providers are singleton and instanced in the {@link Plugin} class
	 * @param providerCode String representation of the provider short code
	 * @return the ProvisionProvider requested
	 */
	@Override
	ProvisionProvider getProvisionProvider(String providerCode) {
		return getAvailableProvisionProviders().find { it.code == providerCode }
	}

	/**
	 * Returns the default provision code for fetching a {@link ProvisionProvider} for this cloud.
	 * This is only really necessary if the provision type code is the exact same as the cloud code.
	 * @return the provision provider code
	 */
	@Override
	String getDefaultProvisionTypeCode() {
		return ProxmoxVeProvisionProvider.PROVISION_PROVIDER_CODE
	}

	@Override
	String getDefaultNetworkServerTypeCode() {
		return 'proxmox-ve.network'
	}

	/**
	 * Returns the Morpheus Context for interacting with data stored in the Main Morpheus Application
	 * @return an implementation of the MorpheusContext for running Future based rxJava queries
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.@context
	}

	/**
	 * Returns the instance of the Plugin class that this provider is loaded from
	 * @return Plugin class contains references to other providers
	 */
	@Override
	Plugin getPlugin() {
		return this.@plugin
	}

	/**
	 * A unique shortcode used for referencing the provided provider. Make sure this is going to be unique as any data
	 * that is seeded or generated related to this provider will reference it by this code.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return CLOUD_PROVIDER_CODE
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return 'Proxmox VE'
	}
}
