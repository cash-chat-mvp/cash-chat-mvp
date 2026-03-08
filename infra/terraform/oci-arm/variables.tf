variable "tenancy_ocid" {
  description = "OCI tenancy OCID."
  type        = string
}

variable "user_ocid" {
  description = "OCI user OCID."
  type        = string
}

variable "fingerprint" {
  description = "API signing key fingerprint."
  type        = string
}

variable "private_key_path" {
  description = "Path to the OCI API private key PEM file."
  type        = string
}

variable "region" {
  description = "OCI region, for example ap-seoul-1."
  type        = string
}

variable "compartment_ocid" {
  description = "Compartment OCID where resources will be created."
  type        = string
}

variable "availability_domain_index" {
  description = "Zero-based availability domain index."
  type        = number
  default     = 0
}

variable "name_prefix" {
  description = "Prefix for network resource names."
  type        = string
  default     = "core-net"
}

variable "instance_name" {
  description = "Compute instance display name."
  type        = string
  default     = "app-server"
}

variable "hostname_label" {
  description = "Hostname label for the primary VNIC."
  type        = string
  default     = "appserver"
}

variable "shape" {
  description = "ARM compute shape."
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "ocpus" {
  description = "OCPU count for the ARM compute instance."
  type        = number
  default     = 4
}

variable "memory_in_gbs" {
  description = "Memory in GB for the ARM compute instance."
  type        = number
  default     = 24
}

variable "image_operating_system" {
  description = "Operating system to use for the image lookup."
  type        = string
  default     = "Oracle Linux"
}

variable "image_operating_system_version" {
  description = "Operating system version for the image lookup."
  type        = string
  default     = "9"
}

variable "ssh_public_key" {
  description = "SSH public key content that will be injected into the instance."
  type        = string
}

variable "cloud_init" {
  description = "Optional cloud-init script content."
  type        = string
  default     = ""
}

variable "vcn_cidr" {
  description = "VCN CIDR block."
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidr" {
  description = "Public subnet CIDR block."
  type        = string
  default     = "10.0.0.0/24"
}

variable "vcn_dns_label" {
  description = "DNS label for the VCN."
  type        = string
  default     = "corenetvcn"
}

variable "subnet_dns_label" {
  description = "DNS label for the subnet."
  type        = string
  default     = "publicsubnet"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to access SSH."
  type        = string
  default     = "0.0.0.0/0"
}

variable "ingress_allowed_cidr" {
  description = "CIDR allowed for open_tcp_ports."
  type        = string
  default     = "0.0.0.0/0"
}

variable "open_tcp_ports" {
  description = "Additional TCP ports to open."
  type        = list(number)
  default     = [80, 443]
}
