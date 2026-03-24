# OCI ARM Compute Terraform

This stack creates one ARM compute instance in OCI together with basic public networking.

Resources created:
- VCN
- Public subnet
- Internet Gateway
- Route Table
- Security List
- ARM compute instance
- Reserved Public IP (static)

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

Retry behavior:
- This stack currently uses the standard `terraform apply` flow only.

Docker and MySQL client bootstrap (optional):
- A ready cloud-init template is provided at `cloud-init/docker-bootstrap.yaml`.
- Enable it in `terraform.tfvars`:
  `cloud_init_path = "cloud-init/docker-bootstrap.yaml"`
- If needed, you can still set inline content with `cloud_init = <<-EOT ... EOT`.
- The template uses lock-aware apt retries to reduce `APT lock` failures from unattended upgrades.
- Important: cloud-init runs on first boot. Existing instances usually need recreation for changes to take effect.
- Important: changing `cloud_init` (`user_data`) can require OCI instance replacement; this does not update an already-running instance in place.
- Verify on the instance:
  `docker --version`
  `docker compose version`
  `mysql --version`
  `systemctl is-active docker`
  `cloud-init status --long`
  `sudo tail -n 200 /var/log/cloud-init-output.log`

Notes:
- Capacity for `VM.Standard.A1.Flex` can vary by region and availability domain.
- If instance creation fails due to capacity, try another `availability_domain_index` or `region`.
- `ssh_allowed_cidr` defaults to `0.0.0.0/0`; narrow it to your public IP when possible.
- `open_tcp_ports` defaults to `80` and `443`; remove ports you do not need.
- This stack disables ephemeral public IP on the VNIC and attaches a reserved public IP only, so upstream ACL rules can rely on a stable IP without IP assignment conflicts.
- If your currently running instance was created with ephemeral public IP enabled, this change may require one instance replacement on the next apply.

Useful outputs:
- `vcn_id`
- `public_subnet_id`
- `instance_public_ip`
- `instance_reserved_public_ip`
- `instance_private_ip`
- `instance_id`

References:
- OCI Compute documentation
  https://docs.oracle.com/iaas/Content/Compute/home.htm
- Terraform provider: `oci_core_instance`
  https://registry.terraform.io/providers/oracle/oci/latest/docs/resources/core_instance
