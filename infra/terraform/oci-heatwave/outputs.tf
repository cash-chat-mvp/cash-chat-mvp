output "db_system_id" {
  description = "OCI MySQL DB System OCID."
  value       = oci_mysql_mysql_db_system.this.id
}

output "db_system_private_ip" {
  description = "Private IP address of the DB System endpoint."
  value       = oci_mysql_mysql_db_system.this.ip_address
}

output "db_system_shape" {
  description = "Provisioned DB System shape."
  value       = oci_mysql_mysql_db_system.this.shape_name
}

output "heatwave_cluster_shape" {
  description = "Provisioned HeatWave cluster shape."
  value       = oci_mysql_heat_wave_cluster.this.shape_name
}

output "heatwave_cluster_size" {
  description = "HeatWave node count."
  value       = oci_mysql_heat_wave_cluster.this.cluster_size
}
