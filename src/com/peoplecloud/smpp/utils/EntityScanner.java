package com.peoplecloud.smpp.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.spi.PersistenceUnitInfo;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.scannotation.AnnotationDB;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

public class EntityScanner implements ApplicationContextAware,
		PersistenceUnitPostProcessor {
	private ApplicationContext applicationContext;
	private String[] classesToExclude = new String[] {};
	private String entityScanPathHook = ".entityScanPath";
	private String[] targetPersistenceUnits = new String[] {};
	private String[] locationPatterns;

	public String[] getLocationPatterns() {
		return locationPatterns;
	}

	public void setLocationPatterns(String... locationPatterns) {
		this.locationPatterns = locationPatterns;
	}

	public String[] getTargetPersistenceUnits() {
		return targetPersistenceUnits;
	}

	public void setTargetPersistenceUnits(String... targetPersistenceUnits) {
		this.targetPersistenceUnits = targetPersistenceUnits;
	}

	public String getEntityScanPathHook() {
		return entityScanPathHook;
	}

	public void setEntityScanPathHook(String entityScanPathHook) {
		this.entityScanPathHook = entityScanPathHook;
	}

	public String[] getClassesToExclude() {
		return classesToExclude;
	}

	public void setClassesToExclude(String... classesToExclude) {
		this.classesToExclude = classesToExclude;
	}

	private Log logger = LogFactory.getLog(getClass());

	public String[] scanPath() {
		return scanPath(locationPatterns);
	}

	public String[] scanPath(String... locationPatterns) {
		try {
			Set<String> entities = new HashSet<String>();

			for (String locationPattern : locationPatterns) {
				Resource[] resources = applicationContext
						.getResources(locationPattern);
				Set<URL> urls = new HashSet<URL>();
				for (Resource resource : resources) {
					System.out.println(":::::::" + resource.getFilename());
					System.out.println(":::::::" + locationPattern);
					String current = new java.io.File(".").getCanonicalPath();
					System.out.println("Current dir:" + current);
					String currentDir = System.getProperty("user.dir");
					System.out
							.println("Current dir using System:" + currentDir);
					urls.add(new URL("file://" + System.getProperty("user.dir")
							+ File.separator + locationPattern));
				}

				entities.addAll(discoverEntities(urls.toArray(new URL[] {})));
			}

			Resource entityScanPathHookedResource = getEntityScanPathHookedResource();

			if (entityScanPathHookedResource != null) {
				entities.addAll(discoverEntities(new URL[] { entityScanPathHookedResource
						.getURL() }));
			}

			Set<String> excludedEntities = identifyExcludedEntities(entities);
			entities.removeAll(excludedEntities);

			return entities.toArray(new String[] {});

		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private Resource getEntityScanPathHookedResource()
			throws MalformedURLException, IOException {
		Resource resource = applicationContext.getResource("classpath:"
				+ getEntityScanPathHook());

		if (resource.exists()) {
			String absPath = resource.getFile().getAbsolutePath();
			String rootPath = absPath.substring(0, absPath.length()
					- ("META-INF/" + getEntityScanPathHook()).length());
			if (!rootPath.startsWith("/")) {
				rootPath = "/" + rootPath;
			}

			return new UrlResource("file:" + rootPath);
		} else {
			if (logger.isWarnEnabled()) {
				logger.warn("entityScanPathHook :"
						+ entityScanPathHook
						+ " doesn't exist in your classpath. If you use persistence.xml "
						+ "located in other than classpath:/META-INF folder "
						+ "your entities may not be scanned by JPA Provider");
			}
			return null;
		}
	}

	private Set<String> identifyExcludedEntities(Set<String> entities) {
		AntPathMatcher antPathMatcher = new AntPathMatcher();
		antPathMatcher.setPathSeparator(".");
		Set<String> excludedEntities = new HashSet<String>();
		for (String entity : entities) {
			for (String pattern : classesToExclude) {
				if (antPathMatcher.match(pattern, entity)) {
					if (logger.isDebugEnabled()) {
						logger.debug(entity + " is excluded with pattern :"
								+ pattern);
					}
					excludedEntities.add(entity);
					break;
				}
			}
		}
		return excludedEntities;
	}

	private Set<String> discoverEntities(URL[] resourceUrls) throws IOException {
		AnnotationDB annotationDB = new AnnotationDB();
		annotationDB.scanArchives(resourceUrls);
		Set<String> entities = annotationDB.getAnnotationIndex().get(
				"javax.persistence.Entity");
		Set<String> embeddables = annotationDB.getAnnotationIndex().get(
				"javax.persistence.Embeddable");
		Set<String> mappedSuperClasses = annotationDB.getAnnotationIndex().get(
				"javax.persistence.MappedSuperclass");
		if (entities == null) {
			entities = new HashSet<String>();
		}
		if (embeddables != null) {
			entities.addAll(embeddables);
		}
		if (mappedSuperClasses != null) {
			entities.addAll(mappedSuperClasses);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Discovered entities :"
					+ StringUtils.collectionToCommaDelimitedString(entities));
		}
		return entities;
	}

	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	public void postProcessPersistenceUnitInfo(MutablePersistenceUnitInfo pui) {
		if (isTargetPersistenceUnit(pui)) {
			String[] entities = scanPath(locationPatterns);
			for (String entity : entities) {
				pui.addManagedClassName(entity);
			}
		} else {
			if (logger.isWarnEnabled()) {
				logger.warn("Skipping PersistenceUnitInfo with name :"
						+ pui.getPersistenceUnitName());
			}
		}
	}

	private boolean isTargetPersistenceUnit(PersistenceUnitInfo pui) {
		return targetPersistenceUnits.length > 0 ? ArrayUtils.contains(
				targetPersistenceUnits, pui.getPersistenceUnitName()) : true;
	}
}
