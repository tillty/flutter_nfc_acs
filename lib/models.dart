enum AcsModel {
  ACR1255U_J1,
}

/// Describes a physical bluetooth reader, it's [name] and [address].
class AcsDevice {
  const AcsDevice(this.address, {this.model = AcsModel.ACR1255U_J1, this.name});

  final String address;
  final String? name;
  final AcsModel model;
}
