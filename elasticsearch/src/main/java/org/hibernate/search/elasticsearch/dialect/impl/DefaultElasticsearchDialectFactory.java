/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.elasticsearch.dialect.impl;

import java.util.Properties;

import org.hibernate.search.elasticsearch.client.impl.ElasticsearchClient;
import org.hibernate.search.elasticsearch.client.impl.ElasticsearchRequest;
import org.hibernate.search.elasticsearch.client.impl.ElasticsearchResponse;
import org.hibernate.search.elasticsearch.dialect.impl.es2.Elasticsearch2Dialect;
import org.hibernate.search.elasticsearch.dialect.impl.es50.Elasticsearch50Dialect;
import org.hibernate.search.elasticsearch.dialect.impl.es52.Elasticsearch52Dialect;
import org.hibernate.search.elasticsearch.gson.impl.JsonAccessor;
import org.hibernate.search.elasticsearch.logging.impl.Log;
import org.hibernate.search.elasticsearch.util.impl.ElasticsearchClientUtils;
import org.hibernate.search.exception.SearchException;
import org.hibernate.search.util.logging.impl.LoggerFactory;

/**
 * @author Yoann Rodiere
 */
public class DefaultElasticsearchDialectFactory implements ElasticsearchDialectFactory {

	private static final Log log = LoggerFactory.make( Log.class );

	private static final JsonAccessor<String> VERSION_ACCESSOR = JsonAccessor.root().property( "version" ).property( "number" ).asString();

	@Override
	public ElasticsearchDialect createDialect(ElasticsearchClient client, Properties properties) {
		String version;
		try {
			version = getVersion( client );
		}
		catch (RuntimeException e) {
			throw log.failedToDetectElasticsearchVersion( e );
		}

		if ( version.startsWith( "0." ) || version.startsWith( "1." ) ) {
			throw log.unsupportedElasticsearchVersion( version );
		}
		else if ( version.startsWith( "2." ) ) {
			return new Elasticsearch2Dialect();
		}
		else if ( version.startsWith( "5." ) ) {
			if ( version.startsWith( "5.0." ) || version.startsWith( "5.1." ) ) {
				return new Elasticsearch50Dialect();
			}
			else {
				return new Elasticsearch52Dialect();
			}
		}
		else {
			log.unexpectedElasticsearchVersion( version );
			return new Elasticsearch52Dialect();
		}
	}

	private String getVersion(ElasticsearchClient client) {
		ElasticsearchRequest request = ElasticsearchRequest.get().build();
		ElasticsearchResponse response = null;
		try {
			response = client.execute( request );

			if ( !ElasticsearchClientUtils.isSuccessCode( response.getStatusCode() ) ) {
				throw log.elasticsearchRequestFailed( request, response, null );
			}

			return VERSION_ACCESSOR.get( response.getBody() ).get();
		}
		catch (SearchException e) {
			throw e; // Do not add context for those: we expect SearchExceptions to be self-explanatory
		}
		catch (RuntimeException e) {
			throw log.elasticsearchRequestFailed( request, response, e );
		}
	}
}
