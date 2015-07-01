package com.thed.zephyr.jenkins.reporter;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.datatype.DatatypeConfigurationException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.thed.zephyr.jenkins.model.ZephyrConfigModel;
import com.thed.zephyr.jenkins.model.ZephyrInstance;
import com.thed.zephyr.jenkins.utils.ConfigurationValidator;
import com.thed.zephyr.jenkins.utils.URLValidator;
import com.thed.zephyr.jenkins.utils.ZephyrSoapClient;
import com.thed.zephyr.jenkins.utils.rest.Cycle;
import com.thed.zephyr.jenkins.utils.rest.Project;
import com.thed.zephyr.jenkins.utils.rest.Release;
import com.thed.zephyr.jenkins.utils.rest.ServerInfo;
import com.thed.zephyr.jenkins.reporter.ZephyrConstants;

@Extension
public final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

	private List<ZephyrInstance> zephyrInstances;
	Map<Long, String> projects;
	Map<Long, String> releases;
	Map<Long, String> cycles;
	private String tempUserName;
	private String tempPassword;

	public List<ZephyrInstance> getZephyrInstances() {
		return zephyrInstances;
	}

	public void setZephyrInstances(List<ZephyrInstance> zephyrInstances) {
		this.zephyrInstances = zephyrInstances;
	}

	
	public DescriptorImpl() {
		super(ZeeReporter.class);
		load();
	}
	
	@Override
	public Publisher newInstance(StaplerRequest req, JSONObject formData)
			throws FormException {
		return super.newInstance(req, formData);
	}

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

	@Override
	public boolean configure(StaplerRequest req, JSONObject formData)
			throws FormException {
		req.bindParameters(this);
		this.zephyrInstances = new ArrayList<ZephyrInstance>();
		Object object = formData.get("zephyrInstances");
		if(object instanceof JSONArray) {
			JSONArray jArr = (JSONArray) object;
			for (Iterator iterator = jArr.iterator(); iterator.hasNext();) {
				JSONObject jObj = (JSONObject) iterator.next();
				ZephyrInstance zephyrInstance = new ZephyrInstance();
				
				String server = jObj.getString("serverAddress").trim();
				String user = jObj.getString("username").trim();
				String pass = jObj.getString("password").trim();

				zephyrInstance.setServerAddress(server);
				zephyrInstance.setUsername(user);
				zephyrInstance.setPassword(pass);
				
				boolean zephyrServerValidation = ConfigurationValidator.validateZephyrConfiguration(server, user, pass);
				if(zephyrServerValidation) {
					this.zephyrInstances.add(zephyrInstance);
				}
			}
			
			
			
		} else if (object instanceof JSONObject) {
			JSONObject jObj = formData.getJSONObject("zephyrInstances");
			ZephyrInstance zephyrInstance = new ZephyrInstance();
			
			String server = jObj.getString("serverAddress").trim();
			String user = jObj.getString("username").trim();
			String pass = jObj.getString("password").trim();

			zephyrInstance.setServerAddress(server);
			zephyrInstance.setUsername(user);
			zephyrInstance.setPassword(pass);
			
			boolean zephyrServerValidation = ConfigurationValidator.validateZephyrConfiguration(server, user, pass);
			if(zephyrServerValidation) {
				this.zephyrInstances.add(zephyrInstance);
			}
			
		}
		save();
		return super.configure(req, formData);
	}
	
    @Override
    public String getDisplayName() {
        return "Publish test result to Zephyr Enterprise";
    }
    
    public FormValidation doCheckProjectKey(@QueryParameter String value) {
    	if (value.isEmpty()) {
    		return FormValidation.error("You must provide a project key.");
    	} else {
    		return FormValidation.ok();
    	}
    }

	public FormValidation doTestConnection(
			@QueryParameter String serverAddress,
			@QueryParameter String username,
			@QueryParameter String password) {
		
		if (StringUtils.isBlank(serverAddress)) {
			return FormValidation.error("Please enter the server name");
		}

		if (StringUtils.isBlank(username)) {
			return FormValidation.error("Please enter the username");
		}

		if (StringUtils.isBlank(password)) {
			return FormValidation.error("Please enter the password");
		}

		if (!(serverAddress.trim().startsWith("https://") || serverAddress.trim().startsWith("http://"))) {
			return FormValidation.error("Incorrect server address format");
		}
		
		String zephyrURL = URLValidator.validateURL(serverAddress);
		
		if(!zephyrURL.startsWith("http")) {
			return FormValidation.error(zephyrURL);
		}
		
		if (!ServerInfo.findServerAddressIsValidZephyrURL(zephyrURL)) {
			return FormValidation.error("This is not a valid Zephyr Server");
		}
		
		Map<Boolean, String> credentialValidationResultMap = ServerInfo.validateCredentials(zephyrURL, username, password);
		if (credentialValidationResultMap.containsKey(false)) {
			return FormValidation.error(credentialValidationResultMap.get(false));
		}
		
		return FormValidation.ok("Connection to Zephyr has been validated");
	}
    
    
    public ListBoxModel doFillServerAddressItems(@QueryParameter String serverAddress) {
    	
        ListBoxModel m = new ListBoxModel();
        
		if (this.zephyrInstances.size() > 0) {
			
			for (ZephyrInstance s : this.zephyrInstances) {
				m.add(s.getServerAddress());
			}
		} else if (StringUtils.isBlank(serverAddress)
				|| serverAddress.trim().equals(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG)) {
			m.add(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG);
		} else {
			m.add(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG);
		}
        return m;
    }
    
    public ListBoxModel doFillProjectKeyItems(@QueryParameter String serverAddress) {
		ListBoxModel m = new ListBoxModel();
		
		String hostNameWithProtocol = URLValidator.validateURL(serverAddress);;
		
		if (StringUtils.isBlank(serverAddress)
				|| serverAddress.trim().equals(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG)
				|| (this.zephyrInstances.size() == 0)) {
			m.add(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG);
			return m;
		}
    	
    	for (ZephyrInstance z: zephyrInstances) {
    		if(z.getServerAddress().trim().equals(serverAddress)) {
    			tempUserName = z.getUsername();
    			tempPassword = z.getPassword();
    		}
    	}
    	
		projects = Project.getAllProjects(hostNameWithProtocol, tempUserName, tempPassword);
		Set<Entry<Long, String>> projectEntrySet = projects.entrySet();

		for (Iterator<Entry<Long, String>> iterator = projectEntrySet.iterator(); iterator.hasNext();) {
			Entry<Long, String> entry = iterator.next();
			m.add(entry.getValue(), entry.getKey()+"");
		}
    	
        return m;
    }
    
    public ListBoxModel doFillReleaseKeyItems(@QueryParameter String projectKey, @QueryParameter String serverAddress) {
		String hostNameWithProtocol = URLValidator.validateURL(serverAddress);
    	
    	ListBoxModel m = new ListBoxModel();
    	
		if (StringUtils.isBlank(projectKey)
				|| projectKey.trim().equals(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG)
				|| (this.zephyrInstances.size() == 0)) {
			m.add(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG);
			return m;
		}

    	long parseLong = Long.parseLong(projectKey);
    	
		releases = Release.getAllReleasesByProjectID(parseLong, hostNameWithProtocol, tempUserName, tempPassword);
		Set<Entry<Long, String>> releaseEntrySet = releases.entrySet();

		for (Iterator<Entry<Long, String>> iterator = releaseEntrySet.iterator(); iterator.hasNext();) {
			Entry<Long, String> entry = iterator.next();
			m.add(entry.getValue(), entry.getKey()+"");
		}
    	
        return m;

    }
    
    public ListBoxModel doFillCycleKeyItems(@QueryParameter String releaseKey, @QueryParameter String serverAddress) {
		String hostNameWithProtocol = URLValidator.validateURL(serverAddress);;
    	ListBoxModel m = new ListBoxModel();

		if (StringUtils.isBlank(releaseKey)
				|| releaseKey.trim().equals(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG)
				|| (this.zephyrInstances.size() == 0)) {
			m.add(ZephyrConstants.ADD_ZEPHYR_GLOBAL_CONFIG);
			return m;
		}
    	
    	long parseLong = Long.parseLong(releaseKey);

   		cycles = Cycle.getAllCyclesByReleaseID(parseLong, hostNameWithProtocol, tempUserName, tempPassword);
		Set<Entry<Long, String>> releaseEntrySet = cycles.entrySet();

		for (Iterator<Entry<Long, String>> iterator = releaseEntrySet.iterator(); iterator.hasNext();) {
			Entry<Long, String> entry = iterator.next();
			m.add(entry.getValue(), entry.getKey()+"");
		}
		
		m.add("New Cycle", ZephyrConstants.NEW_CYCLE_KEY);
    	
        return m;
    }
    public ListBoxModel doFillCycleDurationItems(@QueryParameter String serverAddress, @QueryParameter String projectKey) {
		String hostNameWithProtocol = URLValidator.validateURL(serverAddress);
		long zephyrProjectId = Long.parseLong(projectKey);
    	ZephyrConfigModel zephyrData = new ZephyrConfigModel();
    	zephyrData.setZephyrURL(hostNameWithProtocol);
    	zephyrData.setUserName(tempUserName);
    	zephyrData.setPassword(tempPassword);
    	zephyrData.setZephyrProjectId(zephyrProjectId);
    	ListBoxModel m = new ListBoxModel();
    	int fetchProjectDuration = 1;
    	try {
			fetchProjectDuration = ZephyrSoapClient.fetchProjectDuration(zephyrData);
		} catch (DatatypeConfigurationException e) {
			e.printStackTrace();
		}
    	
    	if (fetchProjectDuration == -1) {
    		m.add("30 days");
    		m.add("7 days");
        	m.add("1 day");
        	return m;
    	}
    	
    	if (fetchProjectDuration >= 29) {
    		m.add("30 days");
    	}

    	if (fetchProjectDuration >= 6) {
    		m.add("7 days");
    	}
    	m.add("1 day");
    	return m;
    }
}