# OCI ARM Compute Terraform

This stack creates one ARM compute instance in OCI together with basic public networking.

Resources created:
- VCN
- Public subnet
- Internet Gateway
- Route Table
- Security List
- ARM compute instance

Current sizing defaults:
- Shape: `VM.Standard.A1.Flex`
- OCPUs: `4`
- Memory: `24 GB`

Usage:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-arm
copy terraform.tfvars.example terraform.tfvars
notepad terraform.tfvars
terraform init
terraform plan
terraform apply
```

Retry helper:

```powershell
cd C:\Work\CashChat\cash-chat-mvp\infra\terraform\oci-arm
.\retry-apply.ps1 -RunInitFirst
```

Another example:

```powershell
.\retry-apply.ps1 -DelaySeconds 120 -MaxAttempts 100
```

Notes:
- Capacity for `VM.Standard.A1.Flex` can vary by region and availability domain.
- If instance creation fails due to capacity, try another `availability_domain_index` or `region`.
- `ssh_allowed_cidr` defaults to `0.0.0.0/0`; narrow it to your public IP when possible.
- `open_tcp_ports` defaults to `80` and `443`; remove ports you do not need.

Useful outputs:
- `vcn_id`
- `public_subnet_id`
- `instance_public_ip`
- `instance_private_ip`
- `instance_id`

References:
- OCI Compute documentation
  https://docs.oracle.com/iaas/Content/Compute/home.htm
- Terraform provider: `oci_core_instance`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/core_instance
