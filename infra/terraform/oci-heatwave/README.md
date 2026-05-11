# OCI MySQL HeatWave Terraform

This stack creates a MySQL HeatWave environment in OCI using an existing VCN.

Resources created:
- Network Security Group for MySQL access
- Private subnet in the existing VCN
- MySQL DB System
- HeatWave cluster (optional)

Notes:
- This stack reuses an existing VCN instead of creating a new one.
- The DB System is created in a private subnet.
- Inbound MySQL access is limited by `mysql_ingress_cidr`.
- Free-tier friendly defaults are set to `MySQL.Free` and `HeatWave.Free`.
- HeatWave cluster creation is optional and controlled by `enable_heatwave`.
- The exact DB System and HeatWave shapes are configurable through:
  `db_system_shape_name` and `heatwave_shape_name`
- Shape availability depends on region, tenancy, and service limits.

Required inputs:
- `tenancy_ocid`
- `user_ocid`
- `fingerprint`
- `private_key_path`
- `region`
- `compartment_ocid`
- `existing_vcn_id`
- `admin_password`

Usage:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-heatwave
copy terraform.tfvars.example terraform.tfvars
notepad terraform.tfvars
terraform init
terraform plan
terraform apply
```

Example connection:

```bash
# Run from inside the VCN (for example: ARM instance, Bastion, or VPN-connected host)
mysql -h <db_system_private_ip> -u <admin_username> -p
```

If you are reusing the VCN from the ARM stack, get it with:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-arm
terraform output vcn_id
```

References:
- OCI MySQL HeatWave documentation
  https://docs.oracle.com/iaas/mysql-database/
- Terraform provider: `oci_mysql_mysql_db_system`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/mysql_mysql_db_system
- Terraform provider: `oci_mysql_heat_wave_cluster`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/mysql_heat_wave_cluster
