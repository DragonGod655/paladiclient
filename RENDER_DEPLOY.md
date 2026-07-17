# Deploy license web to Render

1. Create a private GitHub repository containing `paladiweb.py`, `requirements.txt`, and `render.yaml`.
2. In Render, create a new Blueprint and select that repository.
3. Set `PALADI_ADMIN_PASSWORD` to a strong unique password before deployment.
4. Deploy. Render creates a persistent disk at `/var/data`; the app stores `licenses.db` there.
5. Open the Render service URL followed by `/admin` to manage keys.
6. In Minecraft, run `.sellpro url https://YOUR-RENDER-URL` and then enter the key.

The existing local `licenses.db` is intentionally ignored by Git. To retain existing keys, upload it to `/var/data/licenses.db` using a secure Render shell or recreate the keys through `/admin` after deployment. Do not commit the database or admin password.
