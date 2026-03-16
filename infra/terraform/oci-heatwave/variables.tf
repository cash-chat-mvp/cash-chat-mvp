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
  description = "Path to OCI API private key PEM."
  type        = string
}

variable "region" {
  description = "OCI region for the MySQL HeatWave deployment."
  type        = string
}

variable "compartment_ocid" {
  description = "Compartment OCID for the DB System."
  type        = string
}

variable "existing_vcn_id" {
  description = "Existing VCN ID to reuse, for example from the ARM stack output."
  type        = string
}

variable "availability_domain_index" {
  description = "Zero-based availability domain index."
  type        = number
  default     = 0
}

variable "name_prefix" {
  description = "Prefix for created network resources."
  type        = string
  default     = "data-net"
}

variable "db_system_name" {
  description = "Display name shown in OCI Console."
  type        = string
  default     = "mysql-db"
}

variable "db_system_description" {
  description = "Optional DB System description."
  type        = string
  default     = "MySQL HeatWave DB System"
}

variable "db_system_shape_name" {
  description = "MySQL DB System shape name."
  type        = string
  default     = "MySQL.Free"
}

variable "hostname_label" {
  description = "Private DNS hostname label for the DB System."
  type        = string
  default     = "mysqldb"
}

variable "admin_username" {
  description = "MySQL admin username."
  type        = string
  default     = "admin"
}

variable "admin_password" {
  description = "MySQL admin password."
  type        = string
  sensitive   = true
}

variable "enable_lakehouse" {
  description = "Enable HeatWave Lakehouse."
  type        = bool
  default     = false
}

variable "enable_heatwave" {
  description = "Create a HeatWave cluster. Keep false for MySQL-only free-tier usage."
  type        = bool
  default     = false
}

variable "heatwave_shape_name" {
  description = "HeatWave cluster shape name."
  type        = string
  default     = "HeatWave.Free"
}

variable "subnet_cidr" {
  description = "Private subnet CIDR block inside the existing VCN."
  type        = string
  default     = "10.0.1.0/24"
}

variable "subnet_dns_label" {
  description = "Subnet DNS label."
  type        = string
  default     = "dbsubnet"
}

variable "mysql_ingress_cidr" {
  description = "CIDR allowed to connect to MySQL ports 3306 and 33060."
  type        = string
  default     = "10.0.0.0/16"
}
