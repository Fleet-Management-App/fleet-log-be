package com.fleetlog.common

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import org.springframework.stereotype.Component
import kotlin.reflect.full.memberProperties

/**
 * Marks every non-nullable property of a Kotlin data class as `required` in the OpenAPI spec.
 * springdoc doesn't do this by itself, so without it the FE's generated DTOs would have every
 * field optional (`id?: string`) even though the API always sends it.
 */
@Component
class KotlinRequiredPropertiesConverter : ModelConverter {

	override fun resolve(type: AnnotatedType, context: ModelConverterContext, chain: Iterator<ModelConverter>): Schema<*>? {
		val schema = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
		val rawClass = Json.mapper().constructType(type.type).rawClass
		if (schema == null || !rawClass.isAnnotationPresent(Metadata::class.java) || !rawClass.kotlin.isData) return schema

		// A resolved model is usually returned as a $ref; the properties live on the defined model
		val model = schema.`$ref`?.let { context.definedModels[it.substringAfterLast('/')] } ?: schema
		val nonNullable = rawClass.kotlin.memberProperties.filterNot { it.returnType.isMarkedNullable }.map { it.name }.toSet()
		model.properties?.keys
			?.filter { it in nonNullable && model.required?.contains(it) != true }
			?.forEach { model.addRequiredItem(it) }
		return schema
	}

}
