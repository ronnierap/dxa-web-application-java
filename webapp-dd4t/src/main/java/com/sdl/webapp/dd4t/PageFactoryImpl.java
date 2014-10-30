package com.sdl.webapp.dd4t;

import com.google.common.base.Strings;
import com.sdl.webapp.common.api.content.ContentProvider;
import com.sdl.webapp.common.api.content.ContentProviderException;
import com.sdl.webapp.common.api.localization.Localization;
import com.sdl.webapp.common.api.mapping.SemanticMapper;
import com.sdl.webapp.common.api.mapping.SemanticMappingException;
import com.sdl.webapp.common.api.mapping.config.SemanticSchema;
import com.sdl.webapp.common.api.model.Entity;
import com.sdl.webapp.common.api.model.Page;
import com.sdl.webapp.common.api.model.Region;
import com.sdl.webapp.common.api.model.ViewModelRegistry;
import com.sdl.webapp.common.api.model.entity.AbstractEntity;
import com.sdl.webapp.common.api.model.page.PageImpl;
import com.sdl.webapp.common.api.model.region.RegionImpl;
import com.sdl.webapp.dd4t.fieldconv.FieldConverterRegistry;
import org.dd4t.contentmodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.sdl.webapp.dd4t.fieldconv.FieldUtils.getStringValue;

/**
 * TODO: Must be modified to work with semantic mapping instead of simple mapping.
 */
@Component
public class PageFactoryImpl implements PageFactory {
    private static final Logger LOG = LoggerFactory.getLogger(PageFactoryImpl.class);

    private static final String CORE_MODULE_NAME = "core";

    private static final String PAGE_VIEW_PREFIX = CORE_MODULE_NAME + "/page/";
    private static final String REGION_VIEW_PREFIX = CORE_MODULE_NAME + "/region/";
    private static final String ENTITY_VIEW_PREFIX = CORE_MODULE_NAME + "/entity/";

    // TODO: Get rid of circular dependency between DD4TContentProvider and PageFactoryImpl, this prevents constructor injection
    @Autowired
    private ContentProvider contentProvider;

    private final ViewModelRegistry viewModelRegistry;

    private final SemanticMapper semanticMapper;

    private final FieldConverterRegistry fieldConverterRegistry;

    @Autowired
    public PageFactoryImpl(ViewModelRegistry viewModelRegistry, SemanticMapper semanticMapper,
                           FieldConverterRegistry fieldConverterRegistry) {
        this.viewModelRegistry = viewModelRegistry;
        this.semanticMapper = semanticMapper;
        this.fieldConverterRegistry = fieldConverterRegistry;
    }

    @Override
    public Page createPage(GenericPage genericPage, Localization localization) throws ContentProviderException {
        PageImpl page = new PageImpl();

        page.setId(genericPage.getId());
        page.setTitle(genericPage.getTitle());

        String localizationPath = localization.getPath();
        if (!localizationPath.endsWith("/")) {
            localizationPath = localizationPath + "/";
        }

        // Get and add includes
        final String pageTypeId = genericPage.getPageTemplate().getId().split("-")[1];
        for (String include : localization.getIncludes(pageTypeId)) {
            final String includeUrl = localizationPath + include;
            final Page includePage = contentProvider.getPageModel(includeUrl, localization);
            page.getIncludes().put(includePage.getTitle(), includePage);
        }

        Map<String, RegionImpl> regions = new LinkedHashMap<>();

        for (ComponentPresentation cp : genericPage.getComponentPresentations()) {
            final Entity entity = createEntity(cp, localization);

            final Map<String, Field> templateMeta = cp.getComponentTemplate().getMetadata();
            if (templateMeta != null) {
                final String regionName = getStringValue(templateMeta, "regionView");
                if (!Strings.isNullOrEmpty(regionName)) {
                    RegionImpl region = regions.get(regionName);
                    if (region == null) {
                        LOG.debug("Creating region: {}", regionName);
                        region = new RegionImpl();

                        region.setName(regionName);
                        region.setModule(CORE_MODULE_NAME);
                        region.setViewName(REGION_VIEW_PREFIX + regionName);

                        regions.put(regionName, region);
                    }

                    region.getEntities().add(entity);
                }
            }
        }

        Map<String, Region> regionMap = new HashMap<>();
        regionMap.putAll(regions);
        page.setRegions(regionMap);

        final String pageViewName = getPageViewName(genericPage);
        page.setViewName(PAGE_VIEW_PREFIX + pageViewName);

        return page;
    }

    private String getEntityIdFromComponentId(String componentId) {
        return componentId.split("-")[1];
    }

    private Entity createEntity(ComponentPresentation cp, Localization localization) throws ContentProviderException {
        final GenericComponent component = cp.getComponent();

        final Map<String, Field> templateMeta = cp.getComponentTemplate().getMetadata();
        if (templateMeta != null) {
            final String componentId = component.getId();

            final String viewName = getStringValue(templateMeta, "view");
            LOG.debug("{}: viewName: {}", componentId, viewName);

            final Class<? extends Entity> entityClass = viewModelRegistry.getViewEntityClass(viewName);
            if (entityClass == null) {
                throw new ContentProviderException("Cannot determine entity type for view name: '" + viewName +
                        "'. Please make sure that an entry is registered for this view name in the ViewModelRegistry.");
            }

            LOG.debug("{}: Creating entity of type: {}", componentId, entityClass.getName());

            final SemanticSchema semanticSchema = localization.getSemanticSchemas().get(
                    Long.parseLong(component.getSchema().getId().split("-")[1]));

            final AbstractEntity entity;
            try {
                entity = (AbstractEntity) semanticMapper.createEntity(entityClass, semanticSchema.getSemanticFields(),
                        new DD4TSemanticFieldDataProvider(component, fieldConverterRegistry));
            } catch (SemanticMappingException e) {
                throw new ContentProviderException(e);
            }

            entity.setId(getEntityIdFromComponentId(componentId));
            entity.setViewName(ENTITY_VIEW_PREFIX + viewName);

            return entity;
        }

        return null;
    }

    private String getPageViewName(GenericPage genericPage) {
        final PageTemplate pageTemplate = genericPage.getPageTemplate();
        if (pageTemplate != null) {
            return getStringValue(pageTemplate.getMetadata(), "view");
        }

        return null;
    }
}
