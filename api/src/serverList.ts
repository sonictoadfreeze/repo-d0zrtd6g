/**
 * Returns the list of proxy share-links served to a subscriber.
 * In production these come from your node inventory / DB; here it is a static demo set.
 */
export function serverLinksForKey(_key: string): string[] {
  return [
    "vless://UUID@node1.example.com:443?encryption=none&flow=xtls-rprx-vision&type=tcp&security=reality&sni=example.com&fp=chrome&pbk=PUBKEY&sid=SID#%F0%9F%87%B3%F0%9F%87%B1%20InkVPN%20NL",
    "vless://UUID@node2.example.com:8443?encryption=none&type=grpc&serviceName=ink&security=reality&sni=example.com&fp=chrome&pbk=PUBKEY&sid=SID#%F0%9F%87%AB%F0%9F%87%AE%20InkVPN%20FI",
  ];
}
