/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.elasticsearch.schema.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.search.elasticsearch.logging.impl.Log;
import org.hibernate.search.elasticsearch.schema.impl.model.DataType;
import org.hibernate.search.elasticsearch.schema.impl.model.DynamicType;
import org.hibernate.search.elasticsearch.schema.impl.model.IndexMetadata;
import org.hibernate.search.elasticsearch.schema.impl.model.IndexType;
import org.hibernate.search.elasticsearch.schema.impl.model.PropertyMapping;
import org.hibernate.search.elasticsearch.schema.impl.model.TypeMapping;
import org.hibernate.search.util.impl.CollectionHelper;
import org.hibernate.search.util.logging.impl.LoggerFactory;

import com.google.gson.JsonPrimitive;

/**
 * The default {@link ElasticsearchSchemaValidator} implementation.
 * <strong>Important implementation note:</strong> unexpected attributes (i.e. those not mapped to a field in TypeMapping)
 * are totally ignored. This allows users to leverage Elasticsearch features that are not supported in
 * Hibernate Search, by setting those attributes manually.
 * @author Yoann Rodiere
 */
public class DefaultElasticsearchSchemaValidator implements ElasticsearchSchemaValidator {

	private static final Log LOG = LoggerFactory.make( Log.class );

	private static final double DEFAULT_DOUBLE_DELTA = 0.001;
	private static final float DEFAULT_FLOAT_DELTA = 0.001f;

	private static final List<String> DEFAULT_DATE_FORMAT;
	static {
		List<String> formats = new ArrayList<>();
		formats.add( "strict_date_optional_time" );
		formats.add( "epoch_millis" );
		DEFAULT_DATE_FORMAT = CollectionHelper.toImmutableList( formats );
	}

	public DefaultElasticsearchSchemaValidator() {
	}

	@Override
	public void validate(IndexMetadata expectedIndexMetadata, IndexMetadata actualIndexMetadata) {
		// Unknown mappings are ignored, we only validate expected mappings
		for ( Map.Entry<String, TypeMapping> entry : expectedIndexMetadata.getMappings().entrySet() ) {
			String mappingName = entry.getKey();
			TypeMapping expectedTypeMapping = entry.getValue();
			TypeMapping actualTypeMapping = actualIndexMetadata.getMappings().get( mappingName );

			if ( actualTypeMapping == null ) {
				throw LOG.mappingMissing( mappingName );
			}

			try {
				validateTypeMapping( expectedTypeMapping, actualTypeMapping );
			}
			catch (ElasticsearchSchemaValidationException e) {
				throw LOG.mappingInvalid( mappingName, e );
			}
		}
	}

	private void validateTypeMapping(TypeMapping expectedMapping, TypeMapping actualMapping) {
		validateEqualWithDefault( "dynamic", expectedMapping.getDynamic(), actualMapping.getDynamic(), DynamicType.TRUE );
		validateTypeMappingProperties( expectedMapping, actualMapping );
	}

	/**
	 * Validate that two values are equal, using a given default value when null is encountered on either value.
	 * <p>Useful to take into account the fact that Elasticsearch has default values for attributes.
	 */
	private static <T> void validateEqualWithDefault(String attributeName, T expectedValue, T actualValue,
			T defaultValueForNulls) {
		Object defaultedExpectedValue = expectedValue == null ? defaultValueForNulls : expectedValue;
		Object defaultedActualValue = actualValue == null ? defaultValueForNulls : actualValue;
		if ( ! Objects.equals( defaultedExpectedValue, defaultedActualValue ) ) {
			// Don't show the defaulted actual value, this might confuse users
			throw LOG.mappingInvalidAttributeValue( attributeName, defaultedExpectedValue, actualValue );
		}
	}

	/**
	 * Variation of {@link #validateEqualWithDefault(String, Object, Object, Object)} for floats.
	 */
	private static <T> void validateEqualWithDefault(String attributeName, Float expectedValue, Float actualValue,
			float delta, Float defaultValueForNulls) {
		Float defaultedExpectedValue = expectedValue == null ? defaultValueForNulls : expectedValue;
		Float defaultedActualValue = actualValue == null ? defaultValueForNulls : actualValue;
		if ( defaultedExpectedValue == null || defaultedActualValue == null ) {
			if ( defaultedExpectedValue == defaultedActualValue ) {
				// Both null
				return;
			}
			else {
				// One null and one non-null
				// Don't show the defaulted actual value, this might confuse users
				throw LOG.mappingInvalidAttributeValue( attributeName, defaultedExpectedValue, actualValue );
			}
		}
		else {
			if ( Float.compare( defaultedExpectedValue, defaultedActualValue ) == 0 ) {
				return;
			}
			if ( Math.abs( defaultedExpectedValue - defaultedActualValue ) > delta ) {
				// Don't show the defaulted actual value, this might confuse users
				throw LOG.mappingInvalidAttributeValue( attributeName, defaultedExpectedValue, actualValue );
			}
		}
	}

	/**
	 * Variation of {@link #validateEqualWithDefault(String, Object, Object, Object)} for doubles.
	 */
	private static <T> void validateEqualWithDefault(String attributeName, Double expectedValue, Double actualValue,
			double delta, Double defaultValueForNulls) {
		Double defaultedExpectedValue = expectedValue == null ? defaultValueForNulls : expectedValue;
		Double defaultedActualValue = actualValue == null ? defaultValueForNulls : actualValue;
		if ( defaultedExpectedValue == null || defaultedActualValue == null ) {
			if ( defaultedExpectedValue == defaultedActualValue ) {
				// Both null
				return;
			}
			else {
				// One null and one non-null
				// Don't show the defaulted actual value, this might confuse users
				throw LOG.mappingInvalidAttributeValue( attributeName, defaultedExpectedValue, actualValue );
			}
		}
		if ( Double.compare( defaultedExpectedValue, defaultedActualValue ) == 0 ) {
			return;
		}
		if ( Math.abs( defaultedExpectedValue - defaultedActualValue ) > delta ) {
			// Don't show the defaulted actual value, this might confuse users
			throw LOG.mappingInvalidAttributeValue( attributeName, defaultedExpectedValue, actualValue );
		}
	}

	private void validateTypeMappingProperties(TypeMapping expectedMapping, TypeMapping actualMapping) {
		// Unknown properties are ignored, we only validate expected properties
		Map<String, PropertyMapping> expectedPropertyMappings = expectedMapping.getProperties();
		Map<String, PropertyMapping> actualPropertyMappings = actualMapping.getProperties();
		if ( expectedPropertyMappings != null ) {
			for ( Map.Entry<String, PropertyMapping> entry : expectedPropertyMappings.entrySet() ) {
				String propertyName = entry.getKey();
				PropertyMapping expectedPropertyMapping = entry.getValue();

				if ( actualPropertyMappings == null ) {
					throw LOG.mappingPropertyMissing( propertyName );
				}

				PropertyMapping actualPropertyMapping = actualPropertyMappings.get( propertyName );
				if ( actualPropertyMapping == null ) {
					throw LOG.mappingPropertyMissing( propertyName );
				}

				try {
					validatePropertyMapping( expectedPropertyMapping, actualPropertyMapping );
				}
				catch (ElasticsearchSchemaValidationException e) {
					throw LOG.mappingPropertyInvalid( propertyName, e );
				}
			}
		}
	}

	private void validatePropertyMapping(PropertyMapping expectedMapping, PropertyMapping actualMapping) {
		validateEqualWithDefault( "type", expectedMapping.getType(), actualMapping.getType(), DataType.OBJECT );

		List<String> formatDefault = DataType.DATE.equals( expectedMapping.getType() )
				? DEFAULT_DATE_FORMAT : Collections.<String>emptyList();
		validateEqualWithDefault( "format", expectedMapping.getFormat(), actualMapping.getFormat(), formatDefault );

		validateEqualWithDefault( "boost", expectedMapping.getBoost(), actualMapping.getBoost(), DEFAULT_FLOAT_DELTA, 1.0f );

		// See Elasticsearch doc: this attribute's default value depends on the data type.
		IndexType indexDefault = DataType.STRING.equals( expectedMapping.getType() ) ? IndexType.ANALYZED : IndexType.NOT_ANALYZED;
		validateEqualWithDefault( "index", expectedMapping.getIndex(), actualMapping.getIndex(), indexDefault );

		/*
		 * Elasticsearch documentation (2.3) says doc_values is true by default on fields
		 * supporting it, but tests show it's wrong.
		 */
		validateEqualWithDefault( "doc_values", expectedMapping.getDocValues(), actualMapping.getDocValues(), false );

		validateEqualWithDefault( "store", expectedMapping.getStore(), actualMapping.getStore(), false );

		validateJsonPrimitive( expectedMapping.getType(), "null_value", expectedMapping.getNullValue(), actualMapping.getNullValue() );

		validateEqualWithDefault( "analyzer", expectedMapping.getAnalyzer(), actualMapping.getAnalyzer(), null );
		validateTypeMapping( expectedMapping, actualMapping );
	}

	private static void validateJsonPrimitive(DataType type, String attributeName, JsonPrimitive expectedValue, JsonPrimitive actualValue) {
		DataType defaultedType = type == null ? DataType.OBJECT : type;

		// We can't just use equal, mainly because of floating-point numbers

		switch ( defaultedType ) {
			case DOUBLE:
				if ( expectedValue.isNumber() && actualValue.isNumber() ) {
					validateEqualWithDefault( attributeName, expectedValue.getAsDouble(), actualValue.getAsDouble(),
							DEFAULT_DOUBLE_DELTA, null );
				}
				else {
					throw LOG.mappingInvalidAttributeValue( attributeName, expectedValue, actualValue );
				}
				break;
			case FLOAT:
				if ( expectedValue.isNumber() && actualValue.isNumber() ) {
					validateEqualWithDefault( attributeName, expectedValue.getAsFloat(), actualValue.getAsFloat(),
							DEFAULT_FLOAT_DELTA, null );
				}
				else {
					throw LOG.mappingInvalidAttributeValue( attributeName, expectedValue, actualValue );
				}
				break;
			case INTEGER:
			case LONG:
			case DATE:
			case BOOLEAN:
			case STRING:
			case OBJECT:
			case GEO_POINT:
			default:
				validateEqualWithDefault( attributeName, expectedValue, actualValue, null );
				break;
		}
	}

}