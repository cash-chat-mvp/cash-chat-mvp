output "instance_public_ip" {
  description = "Ephemeral public IP assigned to the instance."
  value       = oci_core_instance.arm_instance.public_ip
}

output "vcn_id" {
  description = "VCN ID for reusing this network from other stacks."
  value       = oci_core_virtual_network.this.id
}

output "public_subnet_id" {
  description = "Public subnet ID used by the ARM instance."
  value       = oci_core_subnet.public.id
}

output "instance_private_ip" {
  description = "Primary private IP of the instance."
  value       = oci_core_instance.arm_instance.private_ip
}

output "instance_id" {
  description = "OCI instance OCID."
  value       = oci_core_instance.arm_instance.id
}

output "image_name" {
  description = "Resolved image name."
  value       = data.oci_core_images.oracle_linux_arm.images[0].display_name
}
