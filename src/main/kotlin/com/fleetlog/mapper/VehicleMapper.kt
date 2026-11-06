package com.fleetlog.mapper

import com.fleetlog.dto.VehicleResponse
import com.fleetlog.entity.Vehicle
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface VehicleMapper {

	fun toResponse(vehicle: Vehicle): VehicleResponse

}
