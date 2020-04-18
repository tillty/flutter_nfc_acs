enum AcsModel {
  ACR1255U_J1,
}

/// Describes a physical bluetooth reader, it's [name] and [address].
class AcsDevice {
  const AcsDevice(this.address, this.name, {this.model = AcsModel.ACR1255U_J1});

  final String address;
  final String name;
  final AcsModel model;
}
